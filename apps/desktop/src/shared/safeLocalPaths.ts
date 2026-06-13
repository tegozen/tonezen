import path from "node:path";

/** Rejects path segments that could escape a storage root (book/track ids, user ids). */
export function isSafeStorageId(id: string): boolean {
  if (!id || id.includes("\0")) return false;
  if (id.includes("..") || id.includes("/") || id.includes("\\")) return false;
  return true;
}

export function isPathUnderRoot(rootDir: string, filePath: string): boolean {
  const root = path.resolve(rootDir);
  const resolved = path.resolve(filePath);
  const relative = path.relative(root, resolved);
  return relative === "" || (!relative.startsWith("..") && !path.isAbsolute(relative));
}

export function resolveTrackDownloadPath(
  downloadsRoot: string,
  bookId: string,
  trackId: string,
): string | null {
  if (!isSafeStorageId(bookId) || !isSafeStorageId(trackId)) return null;
  const target = path.resolve(downloadsRoot, bookId, `${trackId}.mp3`);
  if (!isPathUnderRoot(downloadsRoot, target)) return null;
  return target;
}

export function sanitizeLocalAudioPath(
  filePath: string,
  allowedRoots: readonly string[],
): string | null {
  if (!filePath || filePath.includes("\0")) return null;
  const resolved = path.resolve(filePath);
  for (const root of allowedRoots) {
    if (isPathUnderRoot(root, resolved)) return resolved;
  }
  return null;
}

/** Ensures a signed download URL targets the configured API/storage origin. */
export function assertAllowedDownloadUrl(signedUrl: string, baseUrl: string): void {
  let target: URL;
  let allowed: URL;
  try {
    target = new URL(signedUrl);
    allowed = new URL(baseUrl);
  } catch {
    throw new Error("Invalid download URL");
  }
  if (target.protocol !== "http:" && target.protocol !== "https:") {
    throw new Error("Invalid download URL scheme");
  }
  if (target.origin !== allowed.origin) {
    throw new Error("Download URL origin mismatch");
  }
}
