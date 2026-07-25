import type { RealtimeChannel } from "@supabase/supabase-js";
import type { SupabaseClient } from "@supabase/supabase-js";
import type { BrowserWindow } from "electron";
import { mergeProgressLww } from "@core/progress/progressMerge.js";
import type { AudiobookProgress, StoredSession } from "@core/types.js";
import { LocalDatabase } from "../db/localDatabase.js";
import { createSupabaseClient } from "../session/supabaseClient.js";
import { isAuthSubscriptionError } from "../catalog/catalogRealtimeSync.js";

import { apiV1Url } from "@core/platform/serverPaths.js";

export interface ProgressSyncConfig {
  baseUrl: string;
  anonKey: string;
}

const AUTH_RECOVERY_DELAY_MS = 2000;

type ProgressRow = {
  book_id: string;
  track_id: string;
  position_ms: number;
  updated_at: string;
  user_id?: string;
};

type ProgressPushResponse = {
  skipped?: boolean;
  progress?: ProgressRow;
};

export class ProgressSyncService {
  private supabase: SupabaseClient | null = null;
  private channel: RealtimeChannel | null = null;
  private userId: string | null = null;
  private mainWindow: BrowserWindow | null = null;
  private subscribed = false;
  private recoveryTimer: ReturnType<typeof setTimeout> | null = null;
  private recoveryInFlight = false;

  constructor(
    private getAccessToken: () => string | null,
    private refreshSession: () => Promise<unknown>,
    private isAccessTokenUsable: () => boolean,
    private config: ProgressSyncConfig,
  ) {}

  setMainWindow(window: BrowserWindow | null): void {
    this.mainWindow = window;
  }

  async start(session: StoredSession): Promise<void> {
    this.stop();
    this.userId = session.userId;
    await this.refreshSession();
    if (!this.isAccessTokenUsable()) {
      this.scheduleAuthRecovery();
      return;
    }

    this.supabase = createSupabaseClient(this.config.baseUrl, this.config.anonKey);
    const token = this.getAccessToken();
    if (token) this.supabase.realtime.setAuth(token);

    await this.pullAll();
    await this.flushPending();
    this.recordLastSyncAt();
    this.attachChannel();
  }

  stop(): void {
    if (this.recoveryTimer) {
      clearTimeout(this.recoveryTimer);
      this.recoveryTimer = null;
    }
    void this.channel?.unsubscribe();
    this.channel = null;
    void this.supabase?.removeAllChannels();
    this.supabase = null;
    this.userId = null;
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
    await this.pushProgress(progress);
  }

  async pullAll(): Promise<void> {
    await this.refreshSession();
    const token = this.getAccessToken();
    if (!token) return;

    const res = await fetch(apiV1Url(this.config.baseUrl, "/progress/audiobooks"), {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) return;

    const data = (await res.json()) as {
      progress?: Array<{
        book_id: string;
        track_id: string;
        position_ms: number;
        updated_at: string;
      }>;
    };

    for (const row of data.progress ?? []) {
      this.applyRemote({
        book_id: row.book_id,
        track_id: row.track_id,
        position_ms: row.position_ms,
        updated_at: row.updated_at,
      });
    }
  }

  async flushPending(): Promise<void> {
    for (const progress of LocalDatabase.getPendingProgress()) {
      await this.pushProgress(progress);
    }
  }

  getSyncStatus(): { pendingCount: number; lastSyncAtEpochMs: number | null } {
    return {
      pendingCount: LocalDatabase.getPendingSyncCount(),
      lastSyncAtEpochMs: LocalDatabase.getLastSyncAtEpochMs(),
    };
  }

  async triggerSync(): Promise<void> {
    await this.pullAll();
    await this.flushPending();
    this.recordLastSyncAt();
  }

  private async pushProgress(progress: AudiobookProgress): Promise<void> {
    await this.refreshSession();
    const token = this.getAccessToken();
    if (!token) return;

    const res = await fetch(apiV1Url(this.config.baseUrl, `/progress/audiobooks/${progress.bookId}`), {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        track_id: progress.trackId,
        position_ms: progress.positionMs,
        updated_at: progress.updatedAt,
      }),
    });
    if (!res.ok) return;

    const data = (await res.json().catch(() => null)) as ProgressPushResponse | null;
    if (data?.progress) {
      this.applyRemote(data.progress);
      return;
    }
    LocalDatabase.markProgressSynced(progress.bookId);
  }

  private applyRemote(row: ProgressRow): void {
    const remote: AudiobookProgress = {
      bookId: row.book_id,
      trackId: row.track_id,
      positionMs: row.position_ms,
      updatedAt: row.updated_at,
    };
    const local = LocalDatabase.getProgress(remote.bookId);
    const merged = mergeProgressLww(local, remote);
    if (!merged) return;

    const pendingLocal =
      local?.pendingSync &&
      local.updatedAt &&
      new Date(local.updatedAt) > new Date(remote.updatedAt);
    if (pendingLocal) return;

    LocalDatabase.upsertProgress(merged, false);
    this.mainWindow?.webContents.send("progress:updated", merged);
  }

  private recordLastSyncAt(): void {
    LocalDatabase.setLastSyncAtEpochMs(Date.now());
  }
}
