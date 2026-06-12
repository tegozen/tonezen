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

export type ProgressSkipResult = { skipped: true; progress: ProgressRecord };

/** Returns skip payload when an existing server record wins over the incoming write. */
export function maybeSkipProgressWrite(
  incoming: ProgressRecord,
  existing: ProgressRecord | null | undefined,
): ProgressSkipResult | null {
  if (!existing) return null;
  const winner = mergeProgressLww(incoming, existing);
  if (winner !== incoming) {
    return { skipped: true, progress: winner };
  }
  return null;
}
