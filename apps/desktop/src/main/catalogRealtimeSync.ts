import type { RealtimeChannel } from "@supabase/supabase-js";
import type { SupabaseClient } from "@supabase/supabase-js";
import type { BrowserWindow } from "electron";
import type { StoredSession } from "../shared/types.js";
import type { CatalogSyncService } from "./catalogSync.js";
import { createSupabaseClient } from "./supabaseClient.js";

export interface CatalogRealtimeSyncConfig {
  baseUrl: string;
  anonKey: string;
}

const SYNC_DEBOUNCE_MS = 2000;

export class CatalogRealtimeSyncService {
  private supabase: SupabaseClient | null = null;
  private channel: RealtimeChannel | null = null;
  private debounceTimer: ReturnType<typeof setTimeout> | null = null;
  private syncInFlight = false;
  private mainWindow: BrowserWindow | null = null;

  constructor(
    private catalogSync: CatalogSyncService,
    private config: CatalogRealtimeSyncConfig,
  ) {}

  setMainWindow(window: BrowserWindow | null): void {
    this.mainWindow = window;
  }

  async start(session: StoredSession): Promise<void> {
    this.stop();
    this.supabase = createSupabaseClient(this.config.baseUrl, this.config.anonKey);
    this.supabase.realtime.setAuth(session.accessToken);

    this.channel = this.supabase
      .channel("catalog-global")
      .on(
        "postgres_changes",
        { event: "*", schema: "public", table: "books" },
        () => this.scheduleSync(),
      )
      .on(
        "postgres_changes",
        { event: "*", schema: "public", table: "cycles" },
        () => this.scheduleSync(),
      )
      .on(
        "postgres_changes",
        { event: "*", schema: "public", table: "tracks" },
        () => this.scheduleSync(),
      )
      .subscribe((status, err) => {
        if (status === "SUBSCRIBED") {
          console.info("[catalog-realtime] subscribed to catalog changes");
          return;
        }
        if (status === "CHANNEL_ERROR" || status === "TIMED_OUT") {
          console.error("[catalog-realtime] subscription failed:", status, err);
        }
      });
  }

  stop(): void {
    if (this.debounceTimer) {
      clearTimeout(this.debounceTimer);
      this.debounceTimer = null;
    }
    void this.channel?.unsubscribe();
    this.channel = null;
    void this.supabase?.removeAllChannels();
    this.supabase = null;
  }

  setAccessToken(accessToken: string | null): void {
    if (accessToken && this.supabase) {
      this.supabase.realtime.setAuth(accessToken);
    }
  }

  private scheduleSync(): void {
    if (this.debounceTimer) clearTimeout(this.debounceTimer);
    this.debounceTimer = setTimeout(() => {
      this.debounceTimer = null;
      void this.runSync();
    }, SYNC_DEBOUNCE_MS);
  }

  private async runSync(): Promise<void> {
    if (this.syncInFlight) return;
    this.syncInFlight = true;
    try {
      await this.catalogSync.syncCatalog();
      this.mainWindow?.webContents.send("catalog:updated");
    } catch (err) {
      console.error("[catalog-realtime] sync failed:", err);
    } finally {
      this.syncInFlight = false;
    }
  }
}
