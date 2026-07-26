import type { AudiobookProgress } from "@core/types.js";

/** Shared with Android: same-track position delta that counts as a sync conflict. */
export const PROGRESS_CONFLICT_THRESHOLD_MS = 30_000;

export type ProgressServerSnapshot = {
  trackId: string;
  positionMs: number;
  revision: number;
};

export function mergeProgressByRevision(
  local: AudiobookProgress | null,
  remote: AudiobookProgress | null,
): AudiobookProgress | null {
  if (!local && !remote) return null;
  if (!local) return remote;
  if (!remote) return local;
  return local.revision >= remote.revision ? local : remote;
}

export function getServerSnapshot(
  progress: AudiobookProgress | null | undefined,
): ProgressServerSnapshot | null {
  if (
    progress?.serverTrackId == null ||
    progress.serverPositionMs == null ||
    progress.serverRevision == null
  ) {
    return null;
  }
  return {
    trackId: progress.serverTrackId,
    positionMs: progress.serverPositionMs,
    revision: progress.serverRevision,
  };
}

export function hasProgressSyncConflict(
  playHead: Pick<AudiobookProgress, "trackId" | "positionMs"> | null | undefined,
  snapshot: ProgressServerSnapshot | null | undefined,
): boolean {
  if (!playHead || !snapshot) return false;
  if (playHead.trackId !== snapshot.trackId) return true;
  return Math.abs(playHead.positionMs - snapshot.positionMs) >= PROGRESS_CONFLICT_THRESHOLD_MS;
}

export function progressConflictChoiceKey(
  playHead: Pick<AudiobookProgress, "trackId" | "positionMs">,
  snapshot: ProgressServerSnapshot,
): string {
  return [
    playHead.trackId,
    String(playHead.positionMs),
    snapshot.trackId,
    String(snapshot.positionMs),
    String(snapshot.revision),
  ].join("|");
}

export function shouldPromptProgressSyncConflict(
  progress: AudiobookProgress & { conflictChoiceKey?: string | null },
): boolean {
  const snapshot = getServerSnapshot(progress);
  if (!snapshot || !hasProgressSyncConflict(progress, snapshot)) return false;
  return progress.conflictChoiceKey !== progressConflictChoiceKey(progress, snapshot);
}

/** Allow flush/push when no divergence, or user already chose local for this divergence. */
export function canAutoFlushProgress(
  progress: AudiobookProgress & { conflictChoiceKey?: string | null },
): boolean {
  const snapshot = getServerSnapshot(progress);
  if (!hasProgressSyncConflict(progress, snapshot)) return true;
  if (!snapshot) return true;
  return progress.conflictChoiceKey === progressConflictChoiceKey(progress, snapshot);
}
