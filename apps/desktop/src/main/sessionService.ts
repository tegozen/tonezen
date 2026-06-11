import { safeStorage } from "electron";
import fs from "node:fs";
import path from "node:path";
import { SessionManager } from "../shared/session.js";
import { SupabaseAuthClient, sessionFromGoTrue } from "../shared/supabaseAuth.js";
import type { SessionState, StoredSession } from "../shared/types.js";

const SESSION_FILE = "session.dat";

export interface SessionConfig {
  supabaseUrl: string;
  anonKey: string;
}

export class SessionService {
  private session: StoredSession | null = null;
  private readonly manager = new SessionManager();
  private sessionPath = "";
  private authClient: SupabaseAuthClient | null = null;
  private online = true;

  init(userDataPath: string, config: SessionConfig): void {
    this.sessionPath = path.join(userDataPath, SESSION_FILE);
    this.authClient = new SupabaseAuthClient(config);
    this.session = this.load();
  }

  setOnline(online: boolean): void {
    this.online = online;
  }

  getAccessToken(): string | null {
    return this.session?.accessToken ?? null;
  }

  getSnapshot(): { state: SessionState; userId: string | null } {
    const state = this.manager.resolveState(this.session, this.online);
    return { state, userId: this.session?.userId ?? null };
  }

  async login(email: string, password: string): Promise<StoredSession> {
    if (!this.authClient) throw new Error("SessionService not initialized");
    const result = await this.authClient.signInWithPassword(email, password);
    const session = sessionFromGoTrue(result);
    this.session = session;
    this.persist(session);
    return session;
  }

  logout(): void {
    this.session = null;
    if (fs.existsSync(this.sessionPath)) fs.unlinkSync(this.sessionPath);
  }

  async refreshIfNeeded(): Promise<SessionState> {
    if (!this.session) return "Unauthenticated";
    if (!this.manager.shouldRefresh(this.session, this.online)) {
      return this.manager.resolveState(this.session, this.online);
    }
    if (!this.manager.beginRefresh()) {
      return this.manager.resolveState(this.session, this.online);
    }
    try {
      if (!this.online) {
        return this.manager.resolveState(this.session, false);
      }
      if (!this.authClient || !this.session.refreshToken) {
        return "Unauthenticated";
      }
      const result = await this.authClient.refreshSession(this.session.refreshToken);
      this.session = sessionFromGoTrue(result);
      this.persist(this.session);
      return "AuthenticatedOnline";
    } catch {
      this.logout();
      return "Unauthenticated";
    } finally {
      this.manager.endRefresh();
    }
  }

  private persist(session: StoredSession): void {
    const json = JSON.stringify(session);
    if (safeStorage.isEncryptionAvailable()) {
      const encrypted = safeStorage.encryptString(json);
      fs.writeFileSync(this.sessionPath, encrypted);
    } else {
      fs.writeFileSync(this.sessionPath, json, "utf-8");
    }
  }

  private load(): StoredSession | null {
    if (!this.sessionPath || !fs.existsSync(this.sessionPath)) return null;
    try {
      const raw = fs.readFileSync(this.sessionPath);
      const json = safeStorage.isEncryptionAvailable()
        ? safeStorage.decryptString(raw)
        : raw.toString("utf-8");
      return JSON.parse(json) as StoredSession;
    } catch {
      return null;
    }
  }
}
