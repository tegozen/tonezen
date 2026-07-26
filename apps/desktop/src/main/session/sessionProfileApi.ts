import { avatarUrlWithCacheBust } from "@core/profile/avatarBytes.js";
import { resolveSyncedAvatarUrl, stripAvatarQuery } from "@core/profile/profileSync.js";
import {
  SupabaseAuthClient,
  applyUserProfile,
} from "@core/auth/supabaseAuth.js";
import type { StoredSession } from "@core/types.js";
import { upsertUserProfileMirror, type UserProfileMirrorRow } from "@core/profile/userProfileMirror.js";
import { normalizeAvatarUrl } from "@core/profile/avatarUpload.js";
import type { SessionConfig } from "./sessionStore.js";

export function applyRemoteUserProfile(
  session: StoredSession,
  row: UserProfileMirrorRow,
  sessionConfig: SessionConfig | null,
  persist: (session: StoredSession) => void,
): StoredSession | null {
  if (row.user_id !== session.userId) return null;

  const serverUpdatedAt = row.updated_at ?? null;
  if (serverUpdatedAt && serverUpdatedAt === session.profileUpdatedAt) return null;

  const nextAvatarBase = stripAvatarQuery(
    normalizeAvatarUrl(row.avatar_url, sessionConfig?.baseUrl ?? ""),
  );
  const avatarUrl = resolveSyncedAvatarUrl({
    prevAvatarUrl: session.avatarUrl,
    prevProfileUpdatedAt: session.profileUpdatedAt,
    nextAvatarBase,
    serverUpdatedAt,
    bust: avatarUrlWithCacheBust,
  });

  const next = {
    ...session,
    displayName: row.display_name?.trim() || session.displayName,
    avatarUrl,
    profileUpdatedAt: serverUpdatedAt,
  };
  persist(next);
  return next;
}

export async function updateProfileDisplayName(
  authClient: SupabaseAuthClient,
  session: StoredSession,
  displayName: string,
  persist: (session: StoredSession) => void,
  mirrorProfileToRealtime: (updatedAt: string) => Promise<void>,
): Promise<{ session: StoredSession; displayName: string | null }> {
  const trimmed = displayName.trim();
  if (!trimmed || trimmed === session.displayName) {
    return { session, displayName: session.displayName };
  }
  const user = await authClient.updateUser(session.accessToken, { displayName: trimmed });
  const next = applyUserProfile(session, user);
  persist(next);
  await mirrorProfileToRealtime(user.updated_at ?? new Date().toISOString());
  return { session: next, displayName: next.displayName };
}

export async function uploadProfileAvatar(
  authClient: SupabaseAuthClient,
  session: StoredSession,
  jpegBytes: Uint8Array | number[] | ArrayBuffer,
  persist: (session: StoredSession) => void,
  mirrorProfileToRealtime: (updatedAt: string) => Promise<void>,
): Promise<{ session: StoredSession; avatarUrl: string }> {
  const avatarUrl = await authClient.uploadAvatar(
    session.accessToken,
    session.userId,
    jpegBytes,
  );
  const user = await authClient.updateUser(session.accessToken, {
    avatarUrl: avatarUrl.split("?")[0] ?? avatarUrl,
  });
  const next = {
    ...applyUserProfile(session, user),
    avatarUrl,
    profileUpdatedAt: user.updated_at ?? session.profileUpdatedAt ?? null,
  };
  persist(next);
  await mirrorProfileToRealtime(next.profileUpdatedAt ?? new Date().toISOString());
  return { session: next, avatarUrl: next.avatarUrl ?? avatarUrl };
}

export async function syncProfileFromServer(
  authClient: SupabaseAuthClient,
  session: StoredSession,
  sessionConfig: SessionConfig | null,
  persist: (session: StoredSession) => void,
  refreshIfNeeded: () => Promise<unknown>,
): Promise<StoredSession | null> {
  try {
    await refreshIfNeeded();

    const user = await authClient.getUser(session.accessToken);
    const merged = applyUserProfile(session, user);
    const avatarUrl = resolveSyncedAvatarUrl({
      prevAvatarUrl: session.avatarUrl,
      prevProfileUpdatedAt: session.profileUpdatedAt,
      nextAvatarBase: stripAvatarQuery(
        normalizeAvatarUrl(merged.avatarUrl, sessionConfig?.baseUrl ?? ""),
      ),
      serverUpdatedAt: user.updated_at ?? null,
      bust: avatarUrlWithCacheBust,
    });

    const next = {
      ...merged,
      avatarUrl,
      profileUpdatedAt: user.updated_at ?? null,
    };
    persist(next);
    return next;
  } catch {
    await refreshIfNeeded();
    return null;
  }
}

export async function mirrorProfileToRealtime(
  session: StoredSession,
  sessionConfig: SessionConfig,
  accessToken: string,
  updatedAt: string,
): Promise<void> {
  try {
    await upsertUserProfileMirror(sessionConfig, accessToken, {
      user_id: session.userId,
      display_name: session.displayName,
      avatar_url: stripAvatarQuery(session.avatarUrl),
      updated_at: updatedAt,
    });
  } catch {
    // Realtime mirror is best-effort; local session is already persisted.
  }
}
