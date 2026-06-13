export function coerceAvatarJpegBytes(raw: unknown): Uint8Array {
  if (raw instanceof Uint8Array) return raw;
  if (ArrayBuffer.isView(raw)) {
    return new Uint8Array(raw.buffer, raw.byteOffset, raw.byteLength);
  }
  if (raw instanceof ArrayBuffer) return new Uint8Array(raw);
  if (Array.isArray(raw)) return Uint8Array.from(raw);
  if (raw && typeof raw === "object") {
    const bufferLike = raw as { type?: string; data?: number[] };
    if (bufferLike.type === "Buffer" && Array.isArray(bufferLike.data)) {
      return Uint8Array.from(bufferLike.data);
    }
    const entries = Object.entries(raw as Record<string, number>)
      .filter(([key]) => /^\d+$/.test(key))
      .sort(([a], [b]) => Number(a) - Number(b));
    if (entries.length > 0) {
      return Uint8Array.from(entries.map(([, value]) => value));
    }
  }
  throw new Error("Invalid avatar payload");
}

export function avatarUrlWithCacheBust(url: string): string {
  const stamp = Date.now().toString();
  return url.includes("?") ? `${url}&t=${stamp}` : `${url}?t=${stamp}`;
}
