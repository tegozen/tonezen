import type { AuthConfig } from "./supabaseAuth.js";
import { coerceAvatarJpegBytes } from "./avatarBytes.js";
import { isSafeStorageId } from "./safeLocalPaths.js";

export const AVATAR_FILE_NAME = "avatar.jpg";

export function publicAvatarUrl(baseUrl: string, userId: string): string {
  return `${baseUrl.replace(/\/$/, "")}/storage/v1/object/public/avatars/${userId}/${AVATAR_FILE_NAME}`;
}

const AVATAR_PUBLIC_PATH_RE = /\/storage\/v1\/object\/public\/avatars\/([^/]+)\/avatar\.jpg$/i;

function isAllowedAvatarAbsoluteUrl(url: string, clientBaseUrl: string): boolean {
  let parsed: URL;
  let allowed: URL;
  try {
    parsed = new URL(url);
    allowed = new URL(clientBaseUrl);
  } catch {
    return false;
  }
  const isHttp = parsed.protocol === "https:" || parsed.protocol === "http:";
  if (!isHttp) return false;
  if (parsed.protocol === "http:" && !["localhost", "127.0.0.1"].includes(parsed.hostname)) {
    return false;
  }
  return parsed.origin === allowed.origin && AVATAR_PUBLIC_PATH_RE.test(parsed.pathname);
}

/** Rewrites emulator/host-specific avatar URLs to this client's public base URL. */
export function normalizeAvatarUrl(
  avatarUrl: string | null | undefined,
  clientBaseUrl: string,
): string | null {
  if (!avatarUrl?.trim()) return null;
  const stripped = avatarUrl.trim().split("?")[0] ?? avatarUrl.trim();
  const match = stripped.match(AVATAR_PUBLIC_PATH_RE);
  if (match?.[1] && clientBaseUrl.trim() && isSafeStorageId(match[1])) {
    return publicAvatarUrl(clientBaseUrl, match[1]);
  }
  if (clientBaseUrl.trim() && isAllowedAvatarAbsoluteUrl(stripped, clientBaseUrl)) {
    return stripped;
  }
  return null;
}

export async function uploadAvatarToStorage(
  config: AuthConfig,
  accessToken: string,
  userId: string,
  jpegBytes: Uint8Array | number[] | ArrayBuffer,
): Promise<string> {
  if (!isSafeStorageId(userId)) {
    throw new Error("Invalid user id");
  }
  const bytes = coerceAvatarJpegBytes(jpegBytes);
  const body = bytes.buffer instanceof ArrayBuffer
    ? bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength)
    : Uint8Array.from(bytes).buffer;
  const objectPath = `${userId}/${AVATAR_FILE_NAME}`;
  const url = `${config.baseUrl.replace(/\/$/, "")}/storage/v1/object/avatars/${objectPath}`;
  const response = await fetch(url, {
    method: "POST",
    headers: {
      apikey: config.anonKey,
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "image/jpeg",
      "x-upsert": "true",
    },
    body,
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Avatar upload failed (${response.status}): ${text}`);
  }
  return publicAvatarUrl(config.baseUrl, userId);
}
