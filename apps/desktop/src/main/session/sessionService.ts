import { SessionManager } from "@core/auth/session.js";
import { SupabaseAuthClient } from "@core/auth/supabaseAuth.js";
import type { SessionState, StoredSession } from "@core/types.js";
import type { UserProfileMirrorRow } from "@core/profile/userProfileMirror.js";
import {
  changePassword as changePasswordApi,
  getReferralCode as getReferralCodeApi,
  loginWithPassword,
  registerWithInvite as registerWithInviteApi,
  requestPasswordRecovery as requestPasswordRecoveryApi,
  verifyInviteCode as verifyInviteCodeApi,
} from "./sessionAuthApi.js";
import {
  applyRemoteUserProfile as applyRemoteUserProfileApi,
  mirrorProfileToRealtime,
  syncProfileFromServer as syncProfileFromServerApi,
  updateProfileDisplayName,
  uploadProfileAvatar,
} from "./sessionProfileApi.js";
import { createSessionRefresh } from "./sessionRefresh.js";
import { SessionFileStore, type SessionConfig } from "./sessionStore.js";

export type { SessionConfig } from "./sessionStore.js";

export class SessionService {
  private session: StoredSession | null = null;
  private readonly manager = new SessionManager();
  private readonly store = new SessionFileStore();
  private authClient: SupabaseAuthClient | null = null;
  private online = true;
  private readonly refresh = createSessionRefresh({
    getSession: () => this.session,
    setSession: (session) => {
      this.session = session;
    },
    getOnline: () => this.online,
    getAuthClient: () => this.authClient,
    getManager: () => this.manager,
    logout: () => this.logout(),
    persist: (session) => this.store.persist(session),
    withClientAvatarUrl: (session) => this.store.withClientAvatarUrl(session),
  });

  init(userDataPath: string, config: SessionConfig): void {
    this.store.init(userDataPath, config);
    this.authClient = new SupabaseAuthClient(config);
    this.session = this.store.load();
  }

  applyRemoteUserProfile(row: UserProfileMirrorRow): boolean {
    if (!this.session) return false;
    const next = applyRemoteUserProfileApi(
      this.session,
      row,
      this.store.getConfig(),
      (session) => this.store.persist(session),
    );
    if (!next) return false;
    this.session = next;
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
    this.session = await loginWithPassword(
      this.authClient,
      email,
      password,
      (session) => this.store.withClientAvatarUrl(session),
    );
    this.store.persist(this.session);
    return this.session;
  }

  async verifyInviteCode(code: string): Promise<boolean> {
    if (!this.authClient) throw new Error("SessionService not initialized");
    return verifyInviteCodeApi(this.authClient, code);
  }

  async registerWithInvite(input: {
    inviteCode: string;
    email: string;
    password: string;
    displayName?: string;
  }): Promise<StoredSession> {
    if (!this.authClient) throw new Error("SessionService not initialized");
    return registerWithInviteApi(this.authClient, input, (email, password) =>
      this.login(email, password),
    );
  }

  async requestPasswordRecovery(email: string): Promise<void> {
    if (!this.authClient) throw new Error("SessionService not initialized");
    await requestPasswordRecoveryApi(this.authClient, email);
  }

  async getReferralCode(): Promise<string> {
    await this.refreshIfNeeded();
    if (!this.session || !this.authClient) throw new Error("__not_signed_in__");
    return getReferralCodeApi(this.authClient, this.session.accessToken);
  }

  logout(): void {
    this.session = null;
    this.store.clear();
  }

  async updateProfile(displayName: string): Promise<{ displayName: string | null }> {
    if (!this.online) throw new Error("__account_offline__");
    await this.refreshIfNeeded();
    if (!this.session || !this.authClient) throw new Error("__not_signed_in__");
    const result = await updateProfileDisplayName(
      this.authClient,
      this.session,
      displayName,
      (session) => {
        this.session = session;
        this.store.persist(session);
      },
      (updatedAt) => this.mirrorProfileToRealtime(updatedAt),
    );
    this.session = result.session;
    return { displayName: result.displayName };
  }

  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    if (!this.online) throw new Error("__account_offline__");
    await this.refreshIfNeeded();
    if (!this.session || !this.authClient) throw new Error("__not_signed_in__");
    await changePasswordApi(
      this.authClient,
      this.session.accessToken,
      currentPassword,
      newPassword,
    );
  }

  async uploadAvatar(jpegBytes: Uint8Array | number[] | ArrayBuffer): Promise<{ avatarUrl: string }> {
    if (!this.online) throw new Error("__account_offline__");
    await this.refreshIfNeeded();
    if (!this.session || !this.authClient) throw new Error("__not_signed_in__");
    const result = await uploadProfileAvatar(
      this.authClient,
      this.session,
      jpegBytes,
      (session) => {
        this.session = session;
        this.store.persist(session);
      },
      (updatedAt) => this.mirrorProfileToRealtime(updatedAt),
    );
    this.session = result.session;
    return { avatarUrl: result.avatarUrl };
  }

  async syncProfileFromServer(): Promise<void> {
    if (!this.session || !this.online || !this.authClient) return;

    const next = await syncProfileFromServerApi(
      this.authClient,
      this.session,
      this.store.getConfig(),
      (session) => {
        this.session = session;
        this.store.persist(session);
      },
      () => this.refreshIfNeeded(),
    );
    if (next) this.session = next;
  }

  async refreshIfNeeded(): Promise<SessionState> {
    return this.refresh.refreshIfNeeded();
  }

  private async mirrorProfileToRealtime(updatedAt: string): Promise<void> {
    if (!this.session || !this.store.getConfig() || !this.online) return;
    await mirrorProfileToRealtime(
      this.session,
      this.store.getConfig()!,
      this.session.accessToken,
      updatedAt,
    );
  }
}
