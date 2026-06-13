import type { RealtimeChannel } from "@supabase/supabase-js";
import type { SupabaseClient } from "@supabase/supabase-js";
import type { BrowserWindow } from "electron";
import type { UserProfileMirrorRow } from "../shared/userProfileMirror.js";
import type { StoredSession } from "../shared/types.js";
import { createSupabaseClient } from "./supabaseClient.js";
import type { SessionService } from "./sessionService.js";

export interface ProfileSyncConfig {
  baseUrl: string;
  anonKey: string;
}

export class ProfileSyncService {
  private supabase: SupabaseClient | null = null;
  private channel: RealtimeChannel | null = null;
  private userId: string | null = null;
  private mainWindow: BrowserWindow | null = null;

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
    this.supabase = createSupabaseClient(this.config.baseUrl, this.config.anonKey);
    this.supabase.realtime.setAuth(session.accessToken);

    this.channel = this.supabase
      .channel(`user-profile:${session.userId}`)
      .on(
        "postgres_changes",
        {
          event: "*",
          schema: "public",
          table: "user_profiles",
          filter: `user_id=eq.${session.userId}`,
        },
        (payload) => {
          const row = (payload.new ?? payload.old) as UserProfileMirrorRow | null;
          if (!row?.user_id) return;
          if (this.sessionService.applyRemoteUserProfile(row)) {
            this.notifyRenderer();
          }
        },
      )
      .subscribe();
  }

  stop(): void {
    void this.channel?.unsubscribe();
    this.channel = null;
    void this.supabase?.removeAllChannels();
    this.supabase = null;
    this.userId = null;
  }

  async updateAuth(): Promise<void> {
    const token = this.sessionService.getAccessToken();
    if (token && this.supabase) {
      this.supabase.realtime.setAuth(token);
    }
  }

  private notifyRenderer(): void {
    this.mainWindow?.webContents.send("session:profileUpdated", this.sessionService.getSnapshot());
  }
}
