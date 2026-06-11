import crypto from "node:crypto";

export function signDownloadUrl(
  storagePath: string,
  secret: string,
  ttlSeconds: number,
  baseUrl: string,
  nowSeconds = Math.floor(Date.now() / 1000),
): string {
  const expires = nowSeconds + ttlSeconds;
  const uri = `/download/${storagePath}`;
  const toSign = `${expires}${uri} ${secret}`;
  const md5Hash = crypto.createHash("md5").update(toSign).digest();
  const md5 = md5Hash.toString("base64url");
  return `${baseUrl}${uri}?md5=${md5}&expires=${expires}`;
}

export interface ProgressRecord {
  book_id: string;
  track_id: string;
  position_ms: number;
  updated_at: string;
}

/** Last-write-wins merge for audiobook progress */
export function mergeProgressLww(
  local: ProgressRecord | null,
  remote: ProgressRecord | null,
): ProgressRecord | null {
  if (!local && !remote) return null;
  if (!local) return remote;
  if (!remote) return local;
  return new Date(local.updated_at) >= new Date(remote.updated_at) ? local : remote;
}
