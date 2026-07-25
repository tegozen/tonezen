import type { RealtimeChannel } from "@supabase/supabase-js";
import type { SupabaseClient } from "@supabase/supabase-js";
import type { BrowserWindow } from "electron";
import type { UserProfileMirrorRow } from "@core/profile/userProfileMirror.js";
import type { StoredSession } from "@core/types.js";
import { createSupabaseClient } from "./supabaseClient.js";
import type { SessionService } from "./sessionService.js";
import { isAuthSubscriptionError } from "./catalogRealtimeSync.js";

export interface ProfileSyncConfig {
  baseUrl: string;
  anonKey: string;
}

const AUTH_RECOVERY_DELAY_MS = 2000;

export class ProfileSyncService {
  private supabase: SupabaseClient | null = null;
  private channel: RealtimeChannel | null = null;
  private userId: string | null = null;
  private mainWindow: BrowserWindow | null = null;
  private subscribed = false;
  private recoveryTimer: ReturnType<typeof setTimeout> | null = null;
  private recoveryInFlight = false;

  constructor(
    private sessionService: SessionService,
    private config: ProfileSyncConfig,
  ) {}

  setMainWindow(window: BrowserWindow | null): void {
    this.mainWindow = window;
  }

  async start(session: StoredSession): Promise<void> {
    this.stop();
    this.userId = session.userId;
    await this.sessionService.refreshIfNeeded();
    await this.ensureSubscribed();
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
    await this.sessionService.refreshIfNeeded();
    if (!this.sessionService.getAccessToken()) {
      this.stop();
      return;
    }
    await this.ensureSubscribed();
  }

  private async ensureSubscribed(): Promise<void> {
    if (!this.userId) return;
    if (!this.sessionService.isAccessTokenUsable()) {
      this.scheduleAuthRecovery();
      return;
    }
    const token = this.sessionService.getAccessToken();
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
    if (!this.supabase || !this.userId || !this.sessionService.isAccessTokenUsable()) return;

    this.channel = this.supabase
      .channel(`user-profile:${this.userId}`)
      .on(
        "postgres_changes",
        {
          event: "*",
          schema: "public",
          table: "user_profiles",
          filter: `user_id=eq.${this.userId}`,
        },
        (payload) => {
          const row = (payload.new ?? payload.old) as UserProfileMirrorRow | null;
          if (!row?.user_id) return;
          if (this.sessionService.applyRemoteUserProfile(row)) {
            this.notifyRenderer();
          }
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
      await this.sessionService.refreshIfNeeded();
      if (!this.sessionService.getAccessToken()) {
        this.stop();
        return;
      }
      await this.ensureSubscribed();
    } finally {
      this.recoveryInFlight = false;
    }
  }

  private notifyRenderer(): void {
    this.mainWindow?.webContents.send("session:profileUpdated", this.sessionService.getSnapshot());
  }
}
