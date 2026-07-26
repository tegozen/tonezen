import { safeStorage } from "electron";
import fs from "node:fs";
import path from "node:path";
import { displayNameFromUser } from "@core/auth/supabaseAuth.js";
import type { StoredSession } from "@core/types.js";
import { normalizeAvatarUrl } from "@core/profile/avatarUpload.js";

export const SESSION_FILE = "session.dat";

export interface SessionConfig {
  baseUrl: string;
  anonKey: string;
}

export class SessionFileStore {
  private sessionPath = "";
  private sessionConfig: SessionConfig | null = null;

  init(userDataPath: string, config: SessionConfig): void {
    this.sessionPath = path.join(userDataPath, SESSION_FILE);
    this.sessionConfig = config;
  }

  getConfig(): SessionConfig | null {
    return this.sessionConfig;
  }

  withClientAvatarUrl(session: StoredSession): StoredSession {
    if (!session.avatarUrl || !this.sessionConfig) return session;
    const avatarUrl = normalizeAvatarUrl(session.avatarUrl, this.sessionConfig.baseUrl);
    if (avatarUrl === session.avatarUrl) return session;
    return { ...session, avatarUrl };
  }

  load(): StoredSession | null {
    if (!this.sessionPath || !fs.existsSync(this.sessionPath)) return null;
    if (!safeStorage.isEncryptionAvailable()) {
      console.warn(
        "[session] OS encryption unavailable; ignoring on-disk session.dat (possible legacy plaintext)",
      );
      return null;
    }
    try {
      const raw = fs.readFileSync(this.sessionPath);
      const json = safeStorage.decryptString(raw);
      const parsed = JSON.parse(json) as StoredSession;
      const email = parsed.email ?? "";
      const displayName =
        parsed.displayName ||
        displayNameFromUser({ id: parsed.userId, email }, email);
      return this.withClientAvatarUrl({
        ...parsed,
        email,
        displayName,
      });
    } catch {
      return null;
    }
  }

  persist(session: StoredSession): void {
    if (!safeStorage.isEncryptionAvailable()) {
      console.warn(
        "[session] OS encryption unavailable; keeping session in memory only (not writing session.dat)",
      );
      return;
    }
    const encrypted = safeStorage.encryptString(JSON.stringify(session));
    fs.writeFileSync(this.sessionPath, encrypted, { mode: 0o600 });
  }

  clear(): void {
    if (fs.existsSync(this.sessionPath)) fs.unlinkSync(this.sessionPath);
  }
}
