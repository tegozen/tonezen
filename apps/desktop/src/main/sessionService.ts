import { safeStorage } from "electron";
import fs from "node:fs";
import path from "node:path";
import { SessionManager } from "../shared/session.js";
import { avatarUrlWithCacheBust } from "../shared/avatarBytes.js";
import { resolveSyncedAvatarUrl, stripAvatarQuery } from "../shared/profileSync.js";
import {
  SupabaseAuthClient,
  applyUserProfile,
  displayNameFromUser,
  sessionFromGoTrue,
} from "../shared/supabaseAuth.js";
import type { SessionState, StoredSession } from "../shared/types.js";
import { upsertUserProfileMirror, type UserProfileMirrorRow } from "../shared/userProfileMirror.js";
import { isRefreshAuthFailure } from "../shared/authErrors.js";
import { createRefreshCoordinator } from "../shared/refreshCoordinator.js";
import { normalizeAvatarUrl } from "../shared/avatarUpload.js";

const SESSION_FILE = "session.dat";

export interface SessionConfig {
  baseUrl: string;
  anonKey: string;
}

export class SessionService {
  private session: StoredSession | null = null;
  private readonly manager = new SessionManager();
  private sessionPath = "";
  private authClient: SupabaseAuthClient | null = null;
  private sessionConfig: SessionConfig | null = null;
  private online = true;
  private readonly refreshCoordinator = createRefreshCoordinator<SessionState>();

  init(userDataPath: string, config: SessionConfig): void {
    this.sessionPath = path.join(userDataPath, SESSION_FILE);
    this.sessionConfig = config;
    this.authClient = new SupabaseAuthClient(config);
    this.session = this.load();
  }

  applyRemoteUserProfile(row: UserProfileMirrorRow): boolean {
    if (!this.session || row.user_id !== this.session.userId) return false;

    const serverUpdatedAt = row.updated_at ?? null;
    if (serverUpdatedAt && serverUpdatedAt === this.session.profileUpdatedAt) return false;

    const nextAvatarBase = stripAvatarQuery(
      normalizeAvatarUrl(row.avatar_url, this.sessionConfig?.baseUrl ?? ""),
    );
    const avatarUrl = resolveSyncedAvatarUrl({
      prevAvatarUrl: this.session.avatarUrl,
      prevProfileUpdatedAt: this.session.profileUpdatedAt,
      nextAvatarBase,
      serverUpdatedAt,
      bust: avatarUrlWithCacheBust,
    });

    this.session = {
      ...this.session,
      displayName: row.display_name?.trim() || this.session.displayName,
      avatarUrl,
      profileUpdatedAt: serverUpdatedAt,
    };
    this.persist(this.session);
    return true;
  }

  setOnline(online: boolean): void {
    this.online = online;
  }

  isOnline(): boolean {
    return this.online;
  }

  getAccessToken(): string | null {
    return this.session?.accessToken ?? null;
  }

  getSession(): StoredSession | null {
    return this.session;
  }

  isAccessTokenUsable(): boolean {
    if (!this.session) return false;
    return this.manager.isAccessTokenUsable(this.session);
  }

  getSnapshot(): {
    state: SessionState;
    email: string | null;
    displayName: string | null;
    avatarUrl: string | null;
    memberSinceEpochMs: number | null;
  } {
    const state = this.manager.resolveState(this.session, this.online);
    return {
      state,
      email: this.session?.email || null,
      displayName: this.session?.displayName || null,
      avatarUrl: this.session?.avatarUrl ?? null,
      memberSinceEpochMs: this.session?.memberSinceEpochMs ?? null,
    };
  }

  async login(email: string, password: string): Promise<StoredSession> {
    if (!this.authClient) throw new Error("SessionService not initialized");
    const result = await this.authClient.signInWithPassword(email, password);
    const session = sessionFromGoTrue(result, email);
    this.session = this.withClientAvatarUrl(session);
    this.persist(this.session);
    return this.session;
  }

  logout(): void {
    this.session = null;
    if (fs.existsSync(this.sessionPath)) fs.unlinkSync(this.sessionPath);
  }

  async updateProfile(displayName: string): Promise<{ displayName: string | null }> {
    if (!this.online) throw new Error("__account_offline__");
    await this.refreshIfNeeded();
    if (!this.session || !this.authClient) throw new Error("__not_signed_in__");
    const trimmed = displayName.trim();
    if (!trimmed || trimmed === this.session.displayName) {
      return { displayName: this.session.displayName };
    }
    const user = await this.authClient.updateUser(this.session.accessToken, { displayName: trimmed });
    this.session = applyUserProfile(this.session, user);
    this.persist(this.session);
    await this.mirrorProfileToRealtime(user.updated_at ?? new Date().toISOString());
    return { displayName: this.session.displayName };
  }

  async changePassword(newPassword: string): Promise<void> {
    if (!this.online) throw new Error("__account_offline__");
    await this.refreshIfNeeded();
    if (!this.session || !this.authClient) throw new Error("__not_signed_in__");
    await this.authClient.updateUser(this.session.accessToken, { password: newPassword });
  }

  async uploadAvatar(jpegBytes: Uint8Array | number[] | ArrayBuffer): Promise<{ avatarUrl: string }> {
    if (!this.online) throw new Error("__account_offline__");
    await this.refreshIfNeeded();
    if (!this.session || !this.authClient) throw new Error("__not_signed_in__");
    const avatarUrl = await this.authClient.uploadAvatar(
      this.session.accessToken,
      this.session.userId,
      jpegBytes,
    );
    const user = await this.authClient.updateUser(this.session.accessToken, {
      avatarUrl: avatarUrl.split("?")[0] ?? avatarUrl,
    });
    this.session = {
      ...applyUserProfile(this.session, user),
      avatarUrl,
      profileUpdatedAt: user.updated_at ?? this.session.profileUpdatedAt ?? null,
    };
    this.persist(this.session);
    await this.mirrorProfileToRealtime(this.session.profileUpdatedAt ?? new Date().toISOString());
    return { avatarUrl: this.session.avatarUrl ?? avatarUrl };
  }

  async syncProfileFromServer(): Promise<void> {
    if (!this.session || !this.online || !this.authClient) return;

    try {
      await this.refreshIfNeeded();
      if (!this.session) return;

      const user = await this.authClient.getUser(this.session.accessToken);
      const merged = applyUserProfile(this.session, user);
      const avatarUrl = resolveSyncedAvatarUrl({
        prevAvatarUrl: this.session.avatarUrl,
        prevProfileUpdatedAt: this.session.profileUpdatedAt,
        nextAvatarBase: stripAvatarQuery(
          normalizeAvatarUrl(merged.avatarUrl, this.sessionConfig?.baseUrl ?? ""),
        ),
        serverUpdatedAt: user.updated_at ?? null,
        bust: avatarUrlWithCacheBust,
      });

      this.session = {
        ...merged,
        avatarUrl,
        profileUpdatedAt: user.updated_at ?? null,
      };
      this.persist(this.session);
    } catch {
      await this.refreshIfNeeded();
    }
  }

  async refreshIfNeeded(): Promise<SessionState> {
    if (!this.session) return "Unauthenticated";
    return this.refreshCoordinator.coalesce(
      () => {
        if (!this.session) return false;
        return (
          this.manager.shouldRefresh(this.session, this.online) ||
          (this.online && this.manager.isExpired(this.session))
        );
      },
      () => this.performRefresh(),
      () => (this.session ? this.manager.resolveState(this.session, this.online) : "Unauthenticated"),
    );
  }

  private async performRefresh(): Promise<SessionState> {
    const session = this.session;
    if (!session) return "Unauthenticated";
    try {
      if (!this.online) {
        return this.manager.resolveState(session, false);
      }
      if (!this.authClient || !session.refreshToken) {
        return "Unauthenticated";
      }
      const result = await this.authClient.refreshSession(session.refreshToken);
      this.session = this.withClientAvatarUrl(sessionFromGoTrue(result, session.email));
      this.persist(this.session);
      return "AuthenticatedOnline";
    } catch (error) {
      if (isRefreshAuthFailure(error)) {
        this.logout();
        return "Unauthenticated";
      }
      return this.manager.resolveState(this.session ?? session, this.online);
    }
  }

  private async mirrorProfileToRealtime(updatedAt: string): Promise<void> {
    if (!this.session || !this.sessionConfig || !this.online) return;
    try {
      await upsertUserProfileMirror(this.sessionConfig, this.session.accessToken, {
        user_id: this.session.userId,
        display_name: this.session.displayName,
        avatar_url: stripAvatarQuery(this.session.avatarUrl),
        updated_at: updatedAt,
      });
    } catch {
      // Realtime mirror is best-effort; local session is already persisted.
    }
  }

  private withClientAvatarUrl(session: StoredSession): StoredSession {
    if (!session.avatarUrl || !this.sessionConfig) return session;
    const avatarUrl = normalizeAvatarUrl(session.avatarUrl, this.sessionConfig.baseUrl);
    if (avatarUrl === session.avatarUrl) return session;
    return { ...session, avatarUrl };
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
}
