import type { AudiobookProgress } from "@core/types.js";

/** Last-write-wins merge for audiobook progress */
export function mergeProgressLww(
  local: AudiobookProgress | null,
  remote: AudiobookProgress | null,
): AudiobookProgress | null {
  if (!local && !remote) return null;
  if (!local) return remote;
  if (!remote) return local;
  return new Date(local.updatedAt) >= new Date(remote.updatedAt) ? local : remote;
}
