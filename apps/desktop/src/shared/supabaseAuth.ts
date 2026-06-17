import { uploadAvatarToStorage } from "./avatarUpload.js";
import { avatarUrlWithCacheBust } from "./avatarBytes.js";
import type { StoredSession } from "./types.js";

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
