import { safeStorage } from "electron";
import fs from "node:fs";
import path from "node:path";
import { SessionManager } from "../shared/session.js";
import type { SessionState, StoredSession } from "../shared/types.js";

const SESSION_FILE = "session.dat";

export class SessionService {
  private session: StoredSession | null = null;
  private readonly manager = new SessionManager();
  private sessionPath = "";

  init(userDataPath: string): void {
    this.sessionPath = path.join(userDataPath, SESSION_FILE);
    this.session = this.load();
  }

  getSnapshot(): { state: SessionState; userId: string | null } {
    const online = this.checkOnline();
    const state = this.manager.resolveState(this.session, online);
    return { state, userId: this.session?.userId ?? null };
  }

  loginDemo(_email: string, _password: string): StoredSession {
    const session: StoredSession = {
      userId: "demo-user",
      accessToken: "demo-access",
      refreshToken: "demo-refresh",
      expiresAtEpochSeconds: Math.floor(Date.now() / 1000) + 3600,
    };
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
    const online = this.checkOnline();
    if (!this.manager.shouldRefresh(this.session, online)) {
      return this.manager.resolveState(this.session, online);
    }
    if (!this.manager.beginRefresh()) {
      return this.manager.resolveState(this.session, online);
    }
    try {
      // Demo refresh — production uses Supabase auth.refreshSession()
      this.session = {
        ...this.session,
        accessToken: "refreshed-access",
        expiresAtEpochSeconds: Math.floor(Date.now() / 1000) + 3600,
      };
      this.persist(this.session);
      return "AuthenticatedOnline";
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

  private checkOnline(): boolean {
    // Main process: assume online; renderer passes network state in production
    return true;
  }
}
