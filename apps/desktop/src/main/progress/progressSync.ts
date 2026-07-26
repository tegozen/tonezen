import {
  getServerSnapshot,
  hasProgressSyncConflict,
  progressConflictChoiceKey,
} from "@core/progress/progressMerge.js";
import type { RealtimeChannel } from "@supabase/supabase-js";
import type { SupabaseClient } from "@supabase/supabase-js";
import type { BrowserWindow } from "electron";
import { net } from "electron";
import type { AudiobookProgress, StoredSession } from "@core/types.js";
import { LocalDatabase } from "../db/localDatabase.js";
import { createSupabaseClient } from "../session/supabaseClient.js";
import { isAuthSubscriptionError } from "../catalog/catalogRealtimeSync.js";
import {
  applyRemoteProgress,
  getProgressSyncStatus,
  pullAllProgress,
  recordLastSyncAt,
  triggerProgressSync,
} from "./progressSyncPullMerge.js";
import { flushPendingProgress, pushProgress } from "./progressSyncPush.js";
import type { ProgressRow, ProgressSyncConfig } from "./progressSyncTypes.js";

export type { ProgressSyncConfig } from "./progressSyncTypes.js";

/** Splash/login must fail-open quickly so offline downloads stay usable. */
export const PROGRESS_SPLASH_PULL_TIMEOUT_MS = 4_000;

async function withTimeout<T>(promise: Promise<T>, ms: number): Promise<T | null> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    return await Promise.race([
      promise,
      new Promise<null>((resolve) => {
        timer = setTimeout(() => resolve(null), ms);
      }),
    ]);
  } finally {
    if (timer) clearTimeout(timer);
  }
}

const AUTH_RECOVERY_DELAY_MS = 2000;

export class ProgressSyncService {
  private supabase: SupabaseClient | null = null;
  private channel: RealtimeChannel | null = null;
  private userId: string | null = null;
  private mainWindow: BrowserWindow | null = null;
  private subscribed = false;
  private recoveryTimer: ReturnType<typeof setTimeout> | null = null;
  private recoveryInFlight = false;
  private serverHydrated = false;
  private preferRemoteOnHydrate = false;

  private showSyncTimer: ReturnType<typeof setTimeout> | null = null;
  private lastShowSyncAtMs = 0;
  private onMainWindowShow: (() => void) | null = null;

  constructor(
    private getAccessToken: () => string | null,
    private refreshSession: () => Promise<unknown>,
    private isAccessTokenUsable: () => boolean,
    private config: ProgressSyncConfig,
  ) {}

  setMainWindow(window: BrowserWindow | null): void {
    if (this.mainWindow && this.onMainWindowShow) {
      this.mainWindow.removeListener("show", this.onMainWindowShow);
    }
    this.mainWindow = window;
    this.onMainWindowShow = null;
    if (!window) return;
    // Tray restore / reopen: Realtime may have missed mobile writes — pull again.
    this.onMainWindowShow = () => {
      const now = Date.now();
      if (now - this.lastShowSyncAtMs < 5_000) return;
      if (this.showSyncTimer) clearTimeout(this.showSyncTimer);
      this.showSyncTimer = setTimeout(() => {
        this.showSyncTimer = null;
        if (!net.isOnline() || !this.userId || !this.isAccessTokenUsable()) return;
        this.lastShowSyncAtMs = Date.now();
        void this.triggerSync().catch(() => undefined);
      }, 400);
    };
    window.on("show", this.onMainWindowShow);
  }

  /** Bind active user for local progress reads before splash pull/start. */
  bindUser(session: StoredSession | null): void {
    if (!session) {
      LocalDatabase.setActiveUserId(null);
      this.userId = null;
      return;
    }
    LocalDatabase.setActiveUserId(session.userId);
    this.userId = session.userId;
    if (LocalDatabase.getHydratedUserId() === session.userId) {
      this.serverHydrated = true;
    }
  }

  prepareHydrateFromLocalCache(): void {
    if (this.serverHydrated) return;
    this.preferRemoteOnHydrate = LocalDatabase.countProgressForActiveUser() === 0;
  }

  async start(session: StoredSession, options?: { splashTimeoutMs?: number }): Promise<void> {
    const userChanged = this.userId != null && this.userId !== session.userId;
    const persistedHydrated = LocalDatabase.getHydratedUserId() === session.userId;
    const keepHydration = (this.serverHydrated || persistedHydrated) && !userChanged;

    this.teardownRealtime();
    if (userChanged && this.userId) {
      LocalDatabase.deleteProgressForUser(this.userId);
      LocalDatabase.setHydratedUserId(null);
    }

    LocalDatabase.setActiveUserId(session.userId);
    this.userId = session.userId;
    // Push gate only — must not skip pull (cross-device refresh).
    this.serverHydrated = keepHydration;

    await this.refreshSession();
    if (!this.isAccessTokenUsable()) {
      this.scheduleAuthRecovery();
      return;
    }

    this.supabase = createSupabaseClient(this.config.baseUrl, this.config.anonKey);
    const token = this.getAccessToken();
    if (token) this.supabase.realtime.setAuth(token);

    if (!this.serverHydrated) {
      this.prepareHydrateFromLocalCache();
    }

    // Always pull when online so Desktop picks up progress written on mobile.
    // Hydration flag only gates push, not pull (matches Android syncBestEffort).
    if (net.isOnline()) {
      const timeoutMs = options?.splashTimeoutMs;
      if (timeoutMs != null) {
        const finished = await withTimeout(this.pullAll(), timeoutMs);
        if (finished === null) {
          // Fail-open splash: keep retrying in background (docs S2).
          void this.pullAll()
            .then(async () => {
              if (this.serverHydrated) {
                await this.flushPending();
                this.recordLastSyncAt();
              }
            })
            .catch(() => undefined);
        }
      } else {
        await this.pullAll();
      }
    }

    if (this.serverHydrated) {
      await this.flushPending();
      this.recordLastSyncAt();
    }
    this.attachChannel();
  }

  stop(): void {
    const previousUser = this.userId;
    this.teardownRealtime();
    this.userId = null;
    this.serverHydrated = false;
    this.preferRemoteOnHydrate = false;
    LocalDatabase.setHydratedUserId(null);
    if (previousUser) {
      LocalDatabase.deleteProgressForUser(previousUser);
    }
    LocalDatabase.setActiveUserId(null);
  }

  private teardownRealtime(): void {
    if (this.recoveryTimer) {
      clearTimeout(this.recoveryTimer);
      this.recoveryTimer = null;
    }
    void this.channel?.unsubscribe();
    this.channel = null;
    void this.supabase?.removeAllChannels();
    this.supabase = null;
    this.subscribed = false;
  }

  async updateAuth(): Promise<void> {
    if (!this.userId) return;
    await this.refreshSession();
    if (!this.getAccessToken()) {
      this.stop();
      return;
    }
    await this.ensureSubscribed();
  }

  private async ensureSubscribed(): Promise<void> {
    if (!this.userId) return;
    if (!this.isAccessTokenUsable()) {
      this.scheduleAuthRecovery();
      return;
    }
    const token = this.getAccessToken();
    if (!token) return;

    if (!this.supabase) {
      this.supabase = createSupabaseClient(this.config.baseUrl, this.config.anonKey);
    }
    this.supabase.realtime.setAuth(token);
    if (this.subscribed) return;

    void this.channel?.unsubscribe();
    this.channel = null;
    this.attachChannel();
  }

  private attachChannel(): void {
    if (!this.supabase || !this.userId || !this.isAccessTokenUsable()) return;

    this.channel = this.supabase
      .channel(`audiobook-progress:${this.userId}`)
      .on(
        "postgres_changes",
        {
          event: "*",
          schema: "public",
          table: "audiobook_progress",
          filter: `user_id=eq.${this.userId}`,
        },
        (payload) => {
          const row = (payload.new ?? payload.old) as ProgressRow | null;
          if (!row?.book_id) return;
          this.applyRemote(row);
        },
      )
      .subscribe((status, err) => {
        if (status === "SUBSCRIBED") {
          this.subscribed = true;
          return;
        }
        if (status === "CHANNEL_ERROR" || status === "TIMED_OUT") {
          this.subscribed = false;
          if (isAuthSubscriptionError(err)) {
            this.scheduleAuthRecovery();
          }
        }
      });
  }

  private scheduleAuthRecovery(): void {
    if (this.recoveryTimer || this.recoveryInFlight) return;
    this.recoveryTimer = setTimeout(() => {
      this.recoveryTimer = null;
      void this.recoverSubscription();
    }, AUTH_RECOVERY_DELAY_MS);
  }

  private async recoverSubscription(): Promise<void> {
    if (this.recoveryInFlight || !this.userId) return;
    this.recoveryInFlight = true;
    try {
      await this.refreshSession();
      if (!this.getAccessToken()) {
        this.stop();
        return;
      }
      await this.ensureSubscribed();
    } finally {
      this.recoveryInFlight = false;
    }
  }

  async saveLocal(bookId: string, trackId: string, positionMs: number): Promise<void> {
    const existing = LocalDatabase.getProgress(bookId);
    const progress: AudiobookProgress = {
      bookId,
      trackId,
      positionMs,
      updatedAt: new Date().toISOString(),
      revision: existing?.revision ?? existing?.serverRevision ?? 0,
      serverTrackId: existing?.serverTrackId,
      serverPositionMs: existing?.serverPositionMs,
      serverRevision: existing?.serverRevision,
      conflictChoiceKey: existing?.conflictChoiceKey,
    };
    const snapshot = getServerSnapshot(progress);
    const nextKey =
      snapshot &&
      hasProgressSyncConflict(progress, snapshot) &&
      existing?.conflictChoiceKey
        ? progressConflictChoiceKey(progress, snapshot)
        : null;
    LocalDatabase.upsertProgress(progress, true, { conflictChoiceKey: nextKey });
    if (this.serverHydrated) {
      await this.pushProgress({ ...progress, conflictChoiceKey: nextKey ?? undefined });
    }
  }

  async chooseLocalProgress(bookId: string): Promise<AudiobookProgress | null> {
    const stored = LocalDatabase.getProgress(bookId);
    if (!stored) return null;
    const snapshot = {
      trackId: stored.serverTrackId,
      positionMs: stored.serverPositionMs,
      revision: stored.serverRevision,
    };
    if (snapshot.trackId == null || snapshot.positionMs == null || snapshot.revision == null) {
      return stored;
    }
    const key = progressConflictChoiceKey(stored, {
      trackId: snapshot.trackId,
      positionMs: snapshot.positionMs,
      revision: snapshot.revision,
    });
    LocalDatabase.setConflictChoiceKey(bookId, key);
    const next = LocalDatabase.getProgress(bookId);
    if (next && this.serverHydrated) {
      await this.pushProgress(next);
    }
    return next;
  }

  async chooseServerProgress(bookId: string): Promise<AudiobookProgress | null> {
    const stored = LocalDatabase.getProgress(bookId);
    if (
      !stored ||
      stored.serverTrackId == null ||
      stored.serverPositionMs == null ||
      stored.serverRevision == null
    ) {
      return stored;
    }
    const key = progressConflictChoiceKey(stored, {
      trackId: stored.serverTrackId,
      positionMs: stored.serverPositionMs,
      revision: stored.serverRevision,
    });
    const applied = LocalDatabase.applyServerToPlayHead(
      bookId,
      {
        trackId: stored.serverTrackId,
        positionMs: stored.serverPositionMs,
        revision: stored.serverRevision,
        updatedAt: stored.updatedAt,
      },
      key,
    );
    this.mainWindow?.webContents.send("progress:updated", applied);
    return applied;
  }

  async pullAll(): Promise<void> {
    const preferRemote = this.preferRemoteOnHydrate && !this.serverHydrated;
    const ok = await pullAllProgress(this.getPullMergeDeps(), { preferRemote });
    if (ok) {
      this.serverHydrated = true;
      this.preferRemoteOnHydrate = false;
      if (this.userId) LocalDatabase.setHydratedUserId(this.userId);
    }
  }

  async flushPending(): Promise<void> {
    if (!this.serverHydrated) return;
    await flushPendingProgress(
      this.getPushDeps(),
      (row) => this.applyRemote(row),
      (row) => this.applyPushAccepted(row),
    );
  }

  getSyncStatus(): { pendingCount: number; lastSyncAtEpochMs: number | null } {
    return getProgressSyncStatus();
  }

  async triggerSync(): Promise<void> {
    await triggerProgressSync(() => this.pullAll(), () => this.flushPending());
  }

  private async pushProgress(progress: AudiobookProgress): Promise<void> {
    if (!this.serverHydrated) return;
    await pushProgress(
      this.getPushDeps(),
      progress,
      (row) => this.applyRemote(row),
      (row) => this.applyPushAccepted(row),
    );
  }

  private applyRemote(row: ProgressRow): void {
    applyRemoteProgress(row, this.mainWindow);
  }

  /** Successful PUT — server is authority for play head; always clear pending_sync. */
  private applyPushAccepted(row: ProgressRow): void {
    const revision = Number(row.revision);
    const applied = LocalDatabase.applyServerToPlayHead(
      row.book_id,
      {
        trackId: row.track_id,
        positionMs: row.position_ms,
        revision: Number.isFinite(revision) ? revision : 0,
        updatedAt: row.updated_at,
      },
      null,
    );
    this.mainWindow?.webContents.send("progress:updated", applied);
    this.recordLastSyncAt();
  }

  private recordLastSyncAt(): void {
    recordLastSyncAt();
  }

  private getPullMergeDeps() {
    return {
      config: this.config,
      getAccessToken: () => this.getAccessToken(),
      refreshSession: () => this.refreshSession(),
      mainWindow: this.mainWindow,
    };
  }

  private getPushDeps() {
    return {
      config: this.config,
      getAccessToken: () => this.getAccessToken(),
      refreshSession: () => this.refreshSession(),
    };
  }
}
