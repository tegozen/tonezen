import type { RealtimeChannel } from "@supabase/supabase-js";
import type { SupabaseClient } from "@supabase/supabase-js";
import type { BrowserWindow } from "electron";
import type { StoredSession } from "@core/types.js";
import type { CatalogSyncService } from "./catalogSync.js";
import { createSupabaseClient } from "../session/supabaseClient.js";

export interface CatalogRealtimeSyncConfig {
  baseUrl: string;
  anonKey: string;
}

const SYNC_DEBOUNCE_MS = 2000;
const SUBSCRIPTION_RECOVERY_DELAY_MS = 2000;
const ERROR_LOG_COOLDOWN_MS = 10_000;

export function isAuthSubscriptionError(err: unknown): boolean {
  const parts: string[] = [];
  if (err instanceof Error) {
    parts.push(err.message);
    const cause = err.cause as { reason?: string } | undefined;
    if (cause?.reason) parts.push(cause.reason);
  } else if (err != null) {
    parts.push(String(err));
  }
  return /expired|invalid.*token|jwt/i.test(parts.join(" "));
}

export class CatalogRealtimeSyncService {
  private supabase: SupabaseClient | null = null;
  private channel: RealtimeChannel | null = null;
  private debounceTimer: ReturnType<typeof setTimeout> | null = null;
  private recoveryTimer: ReturnType<typeof setTimeout> | null = null;
  private syncInFlight = false;
  private recoveryInFlight = false;
  private subscribed = false;
  private storedSession: StoredSession | null = null;
  private mainWindow: BrowserWindow | null = null;
  private lastErrorLogMs = 0;
  private lastDeferredLogMs = 0;

  constructor(
    private catalogSync: CatalogSyncService,
    private config: CatalogRealtimeSyncConfig,
    private getAccessToken: () => string | null,
    private refreshSession: () => Promise<unknown>,
    private isAccessTokenUsable: () => boolean,
  ) {}

  setMainWindow(window: BrowserWindow | null): void {
    this.mainWindow = window;
  }

  async start(session: StoredSession): Promise<void> {
    this.stop();
    this.storedSession = session;
    await this.refreshSession();
    await this.ensureSubscribed();
  }

  stop(): void {
    if (this.debounceTimer) {
      clearTimeout(this.debounceTimer);
      this.debounceTimer = null;
    }
    if (this.recoveryTimer) {
      clearTimeout(this.recoveryTimer);
      this.recoveryTimer = null;
    }
    void this.channel?.unsubscribe();
    this.channel = null;
    void this.supabase?.removeAllChannels();
    this.supabase = null;
    this.storedSession = null;
    this.subscribed = false;
  }

  async updateAuth(): Promise<void> {
    if (!this.storedSession) return;
    await this.refreshSession();
    if (!this.getAccessToken()) {
      this.stop();
      return;
    }
    await this.ensureSubscribed();
  }

  private async ensureSubscribed(): Promise<void> {
    if (!this.storedSession) return;
    if (!this.isAccessTokenUsable()) {
      this.logDeferredOnce();
      this.scheduleSubscriptionRecovery();
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
    if (!this.supabase || !this.isAccessTokenUsable()) return;

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
          this.subscribed = true;
          console.info("[catalog-realtime] subscribed to catalog changes");
          return;
        }
        if (status === "CHANNEL_ERROR" || status === "TIMED_OUT") {
          this.subscribed = false;
          this.logSubscriptionError(status, err);
          this.scheduleSubscriptionRecovery();
        }
      });
  }

  private logDeferredOnce(): void {
    const now = Date.now();
    if (now - this.lastDeferredLogMs < ERROR_LOG_COOLDOWN_MS) return;
    this.lastDeferredLogMs = now;
    console.warn("[catalog-realtime] subscription deferred until access token is refreshed");
  }

  private logSubscriptionError(status: string, err: unknown): void {
    const now = Date.now();
    if (now - this.lastErrorLogMs < ERROR_LOG_COOLDOWN_MS) return;
    this.lastErrorLogMs = now;
    console.error("[catalog-realtime] subscription failed:", status, err);
  }

  private scheduleSubscriptionRecovery(): void {
    if (this.recoveryTimer || this.recoveryInFlight) return;
    this.recoveryTimer = setTimeout(() => {
      this.recoveryTimer = null;
      void this.recoverSubscription();
    }, SUBSCRIPTION_RECOVERY_DELAY_MS);
  }

  private async recoverSubscription(): Promise<void> {
    if (this.recoveryInFlight || !this.storedSession) return;
    this.recoveryInFlight = true;
    try {
      await this.refreshSession();
      if (!this.getAccessToken()) {
        this.stop();
        return;
      }
      if (!this.isAccessTokenUsable()) {
        console.warn("[catalog-realtime] subscription recovery waiting for a valid access token");
        this.scheduleSubscriptionRecovery();
        return;
      }
      await this.ensureSubscribed();
    } catch (err) {
      console.error("[catalog-realtime] auth recovery failed:", err);
    } finally {
      this.recoveryInFlight = false;
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
      await this.refreshSession();
      if (!this.isAccessTokenUsable()) return;
      await this.catalogSync.syncCatalog();
      this.mainWindow?.webContents.send("catalog:updated");
    } catch (err) {
      console.error("[catalog-realtime] sync failed:", err);
    } finally {
      this.syncInFlight = false;
    }
  }
}
