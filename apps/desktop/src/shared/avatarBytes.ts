export const MAX_AVATAR_JPEG_BYTES = 5 * 1024 * 1024;

const JPEG_SOI = [0xff, 0xd8] as const;

function assertJpegPayload(bytes: Uint8Array): void {
  if (bytes.byteLength === 0) {
    throw new Error("Empty avatar payload");
  }
  if (bytes.byteLength > MAX_AVATAR_JPEG_BYTES) {
    throw new Error("Avatar payload too large");
  }
  if (bytes[0] !== JPEG_SOI[0] || bytes[1] !== JPEG_SOI[1]) {
    throw new Error("Avatar payload must be JPEG");
  }
}

export function coerceAvatarJpegBytes(raw: unknown): Uint8Array {
  let bytes: Uint8Array;
  if (raw instanceof Uint8Array) {
    bytes = raw;
  } else if (ArrayBuffer.isView(raw)) {
    bytes = new Uint8Array(raw.buffer, raw.byteOffset, raw.byteLength);
  } else if (raw instanceof ArrayBuffer) {
    bytes = new Uint8Array(raw);
  } else if (Array.isArray(raw)) {
    if (raw.length > MAX_AVATAR_JPEG_BYTES) {
      throw new Error("Avatar payload too large");
    }
    bytes = Uint8Array.from(raw);
  } else if (raw && typeof raw === "object") {
    const bufferLike = raw as { type?: string; data?: number[] };
    if (bufferLike.type === "Buffer" && Array.isArray(bufferLike.data)) {
      if (bufferLike.data.length > MAX_AVATAR_JPEG_BYTES) {
        throw new Error("Avatar payload too large");
      }
      bytes = Uint8Array.from(bufferLike.data);
    } else {
      const entries = Object.entries(raw as Record<string, number>)
        .filter(([key]) => /^\d+$/.test(key))
        .sort(([a], [b]) => Number(a) - Number(b));
      if (entries.length === 0) {
        throw new Error("Invalid avatar payload");
      }
      if (entries.length > MAX_AVATAR_JPEG_BYTES) {
        throw new Error("Avatar payload too large");
      }
      bytes = Uint8Array.from(entries.map(([, value]) => value));
    }
  } else {
    throw new Error("Invalid avatar payload");
  }
  assertJpegPayload(bytes);
  return bytes;
}

export function avatarUrlWithCacheBust(url: string): string {
  const stamp = Date.now().toString();
  return url.includes("?") ? `${url}&t=${stamp}` : `${url}?t=${stamp}`;
}
