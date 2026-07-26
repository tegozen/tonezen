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
  /** Block HTTP push until first successful pull — avoids LWW wipe after reinstall. */
  private serverHydrated = false;
  /** Reinstall/empty local: first pull prefers server over fail-open pending zeros. */
  private preferRemoteOnHydrate = false;

  constructor(
    private getAccessToken: () => string | null,
    private refreshSession: () => Promise<unknown>,
    private isAccessTokenUsable: () => boolean,
    private config: ProgressSyncConfig,
  ) {}

  setMainWindow(window: BrowserWindow | null): void {
    this.mainWindow = window;
  }

  prepareHydrateFromLocalCache(): void {
    if (this.serverHydrated) return;
    this.preferRemoteOnHydrate = LocalDatabase.getAllProgress().length === 0;
  }

  async start(session: StoredSession, options?: { splashTimeoutMs?: number }): Promise<void> {
    // Bootstrap may already have pulled; do not clear hydration and reopen a push race
    // while the shell is (or is about to become) interactive.
    const userChanged = this.userId != null && this.userId !== session.userId;
    const keepHydration = this.serverHydrated && !userChanged;
    this.teardownRealtime();
    if (!keepHydration) {
      this.serverHydrated = false;
    }
    this.userId = session.userId;

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
      // Offline: do not wait on fetch — local downloads must open immediately.
      if (net.isOnline()) {
        const timeoutMs = options?.splashTimeoutMs;
        if (timeoutMs != null) {
          await withTimeout(this.pullAll(), timeoutMs);
        } else {
          await this.pullAll();
        }
      }
    }
    if (this.serverHydrated) {
      await this.flushPending();
      this.recordLastSyncAt();
    }
    this.attachChannel();
  }

  stop(): void {
    this.teardownRealtime();
    this.userId = null;
    this.serverHydrated = false;
    this.preferRemoteOnHydrate = false;
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
    const progress: AudiobookProgress = {
      bookId,
      trackId,
      positionMs,
      updatedAt: new Date().toISOString(),
    };
    LocalDatabase.upsertProgress(progress, true);
    if (this.serverHydrated) {
      await this.pushProgress(progress);
    }
  }

  async pullAll(): Promise<void> {
    const preferRemote = this.preferRemoteOnHydrate && !this.serverHydrated;
    const ok = await pullAllProgress(this.getPullMergeDeps(), { preferRemote });
    if (ok) {
      this.serverHydrated = true;
      this.preferRemoteOnHydrate = false;
    }
  }

  async flushPending(): Promise<void> {
    if (!this.serverHydrated) return;
    await flushPendingProgress(this.getPushDeps(), (row) => this.applyRemote(row));
  }

  getSyncStatus(): { pendingCount: number; lastSyncAtEpochMs: number | null } {
    return getProgressSyncStatus();
  }

  async triggerSync(): Promise<void> {
    await triggerProgressSync(() => this.pullAll(), () => this.flushPending());
  }

  private async pushProgress(progress: AudiobookProgress): Promise<void> {
    if (!this.serverHydrated) return;
    await pushProgress(this.getPushDeps(), progress, (row) => this.applyRemote(row));
  }

  private applyRemote(row: ProgressRow): void {
    applyRemoteProgress(row, this.mainWindow);
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
