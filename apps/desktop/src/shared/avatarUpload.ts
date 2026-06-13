import type { AuthConfig } from "./supabaseAuth.js";
import { coerceAvatarJpegBytes } from "./avatarBytes.js";

export const AVATAR_FILE_NAME = "avatar.jpg";

export function publicAvatarUrl(baseUrl: string, userId: string): string {
  return `${baseUrl.replace(/\/$/, "")}/storage/v1/object/public/avatars/${userId}/${AVATAR_FILE_NAME}`;
}

export async function uploadAvatarToStorage(
  config: AuthConfig,
  accessToken: string,
  userId: string,
  jpegBytes: Uint8Array | number[] | ArrayBuffer,
): Promise<string> {
  const bytes = coerceAvatarJpegBytes(jpegBytes);
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
    body: bytes,
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Avatar upload failed (${response.status}): ${text}`);
  }
  return publicAvatarUrl(config.baseUrl, userId);
}
