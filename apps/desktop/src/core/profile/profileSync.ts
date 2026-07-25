export function stripAvatarQuery(url: string | null | undefined): string | null {
  if (!url?.trim()) return null;
  return url.split("?")[0] ?? url;
}

export function resolveSyncedAvatarUrl(input: {
  prevAvatarUrl: string | null | undefined;
  prevProfileUpdatedAt: string | null | undefined;
  nextAvatarBase: string | null;
  serverUpdatedAt: string | null | undefined;
  bust: (url: string) => string;
}): string | null {
  const { prevAvatarUrl, prevProfileUpdatedAt, nextAvatarBase, serverUpdatedAt, bust } = input;
  if (!nextAvatarBase) return null;
  const avatarChanged = stripAvatarQuery(prevAvatarUrl) !== nextAvatarBase;
  const profileChanged = (serverUpdatedAt ?? null) !== (prevProfileUpdatedAt ?? null);
  if (avatarChanged || profileChanged) return bust(nextAvatarBase);
  return prevAvatarUrl ?? bust(nextAvatarBase);
}
