import { uploadAvatarToStorage } from "@core/profile/avatarUpload.js";
import { avatarUrlWithCacheBust } from "@core/profile/avatarBytes.js";
import type { StoredSession } from "@core/types.js";

export interface GoTrueUser {
  id: string;
  email?: string;
  created_at?: string;
  updated_at?: string;
  user_metadata?: Record<string, unknown>;
}

export interface GoTrueSession {
  access_token: string;
  refresh_token: string;
  expires_in: number;
  token_type: string;
  user: GoTrueUser;
}

export function displayNameFromUser(user: GoTrueUser, fallbackEmail = ""): string {
  const meta = user.user_metadata ?? {};
  const fromMeta = meta.full_name ?? meta.display_name;
  if (typeof fromMeta === "string" && fromMeta.trim()) {
    return fromMeta.trim();
  }
  const email = (user.email ?? fallbackEmail).trim();
  const localPart = email.split("@")[0]?.trim();
  if (!localPart) return "";
  return localPart.charAt(0).toUpperCase() + localPart.slice(1);
}

export function avatarUrlFromUser(user: GoTrueUser): string | null {
  const meta = user.user_metadata ?? {};
  const url = meta.avatar_url ?? meta.picture;
  return typeof url === "string" && url.trim() ? url.trim() : null;
}

export function memberSinceFromUser(user: GoTrueUser): number | null {
  if (!user.created_at?.trim()) return null;
  const ms = Date.parse(user.created_at);
  return Number.isNaN(ms) ? null : ms;
}

export interface AuthConfig {
  baseUrl: string;
  anonKey: string;
}

export class SupabaseAuthClient {
  constructor(private config: AuthConfig) {}

  async signInWithPassword(email: string, password: string): Promise<GoTrueSession> {
    return this.tokenRequest({ grant_type: "password", email, password });
  }

  async verifyInviteCode(code: string): Promise<boolean> {
    const response = await this.tonezenApiRequest("/auth/invite/verify", {
      method: "POST",
      body: JSON.stringify({ code }),
    });
    const json = (await response.json()) as { valid?: unknown };
    return json.valid === true;
  }

  async signUpWithInvite(input: {
    inviteCode: string;
    email: string;
    password: string;
    displayName?: string;
  }): Promise<void> {
    await this.tonezenApiRequest("/auth/signup", {
      method: "POST",
      body: JSON.stringify({
        invite_code: input.inviteCode,
        email: input.email,
        password: input.password,
        display_name: input.displayName,
      }),
    });
  }

  async requestPasswordRecovery(email: string): Promise<void> {
    await this.tonezenApiRequest("/auth/password/recovery", {
      method: "POST",
      body: JSON.stringify({ email }),
    });
  }

  async getReferralCode(accessToken: string): Promise<string> {
    const result = await this.tonezenApiRequest("/auth/referral-code", {
      method: "GET",
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });
    const json = (await result.json()) as { code?: unknown };
    if (typeof json.code !== "string") {
      throw new Error("Referral code response missing code");
    }
    return json.code;
  }

  async changePassword(
    accessToken: string,
    currentPassword: string,
    newPassword: string,
  ): Promise<void> {
    await this.tonezenApiRequest("/auth/password", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify({
        current_password: currentPassword,
        password: newPassword,
      }),
    });
  }

  async refreshSession(refreshToken: string): Promise<GoTrueSession> {
    return this.tokenRequest({ grant_type: "refresh_token", refresh_token: refreshToken });
  }

  async getUser(accessToken: string): Promise<GoTrueUser> {
    const url = `${this.config.baseUrl.replace(/\/$/, "")}/auth/v1/user`;
    const response = await fetch(url, {
      method: "GET",
      headers: {
        apikey: this.config.anonKey,
        Authorization: `Bearer ${accessToken}`,
      },
    });
    if (!response.ok) {
      const text = await response.text();
      throw new Error(`Profile fetch failed (${response.status}): ${text}`);
    }
    return (await response.json()) as GoTrueUser;
  }

  async updateUser(
    accessToken: string,
    updates: { displayName?: string; password?: string; avatarUrl?: string },
  ): Promise<GoTrueUser> {
    const body: Record<string, unknown> = {};
    if (updates.displayName != null || updates.avatarUrl != null) {
      const data: Record<string, string> = {};
      if (updates.displayName != null) {
        data.full_name = updates.displayName;
      }
      if (updates.avatarUrl != null) {
        data.avatar_url = updates.avatarUrl;
      }
      body.data = data;
    }
    if (updates.password != null) {
      body.password = updates.password;
    }
    const url = `${this.config.baseUrl.replace(/\/$/, "")}/auth/v1/user`;
    const response = await fetch(url, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        apikey: this.config.anonKey,
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      const text = await response.text();
      throw new Error(`Profile update failed (${response.status}): ${text}`);
    }
    return (await response.json()) as GoTrueUser;
  }

  async uploadAvatar(
    accessToken: string,
    userId: string,
    jpegBytes: Uint8Array | number[] | ArrayBuffer,
  ): Promise<string> {
    return avatarUrlWithCacheBust(
      await uploadAvatarToStorage(this.config, accessToken, userId, jpegBytes),
    );
  }

  private async tokenRequest(body: Record<string, string>): Promise<GoTrueSession> {
    const url = `${this.config.baseUrl.replace(/\/$/, "")}/auth/v1/token?grant_type=${body.grant_type}`;
    const response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        apikey: this.config.anonKey,
        Authorization: `Bearer ${this.config.anonKey}`,
      },
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      const text = await response.text();
      throw new Error(`Auth failed (${response.status}): ${text}`);
    }
    return (await response.json()) as GoTrueSession;
  }

  private async tonezenApiRequest(path: string, init: RequestInit): Promise<Response> {
    const url = `${this.config.baseUrl.replace(/\/$/, "")}/api/v1${path}`;
    const response = await fetch(url, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        ...(init.headers ?? {}),
      },
    });
    if (!response.ok) {
      const text = await response.text();
      throw new Error(`Tonezen auth request failed (${response.status}): ${text}`);
    }
    return response;
  }
}

export function sessionFromGoTrue(result: GoTrueSession, fallbackEmail = ""): StoredSession {
  const email = result.user.email ?? fallbackEmail;
  return applyUserProfile(
    {
      userId: result.user.id,
      email,
      displayName: displayNameFromUser(result.user, email),
      accessToken: result.access_token,
      refreshToken: result.refresh_token,
      expiresAtEpochSeconds: Math.floor(Date.now() / 1000) + result.expires_in,
      profileUpdatedAt: result.user.updated_at ?? null,
    },
    result.user,
  );
}

export function applyUserProfile(session: StoredSession, user: GoTrueUser): StoredSession {
  return {
    ...session,
    displayName: displayNameFromUser(user, session.email),
    memberSinceEpochMs: memberSinceFromUser(user) ?? session.memberSinceEpochMs ?? null,
    avatarUrl: avatarUrlFromUser(user) ?? session.avatarUrl ?? null,
  };
}

export function mergeSessionOnRefresh(previous: StoredSession, refreshed: StoredSession): StoredSession {
  return {
    ...refreshed,
    avatarUrl: refreshed.avatarUrl ?? previous.avatarUrl ?? null,
    profileUpdatedAt: refreshed.profileUpdatedAt ?? previous.profileUpdatedAt ?? null,
    memberSinceEpochMs: refreshed.memberSinceEpochMs ?? previous.memberSinceEpochMs ?? null,
  };
}
