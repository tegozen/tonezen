import type { AudiobookProgress, Track } from "@core/types";

export interface BookContinueState {
  trackTitle: string;
  positionMs: number;
}

export function canContinueBookListening(
  bookId: string,
  tracks: Track[],
  progress: Pick<AudiobookProgress, "bookId" | "trackId" | "positionMs"> | null | undefined,
): BookContinueState | null {
  if (!bookId || !progress || progress.bookId !== bookId || tracks.length === 0) return null;

  const sortedTracks = [...tracks].sort((a, b) => a.sortOrder - b.sortOrder);
  const savedTrack = sortedTracks.find(
    (track) => track.id === progress.trackId && track.bookId === bookId,
  );
  if (!savedTrack) return null;

  const isBookListened = sortedTracks.every(
    (track) =>
      track.sortOrder < savedTrack.sortOrder ||
      (track.id === savedTrack.id && progress.positionMs >= (track.durationMs ?? 0) * 0.95),
  );
  if (isBookListened) return null;

  const progressByTrack = buildBookTrackProgress(
    sortedTracks,
    progress.trackId,
    progress.positionMs,
    null,
    0,
  );
  const fraction = progressByTrack.get(savedTrack.id);
  if (fraction == null || fraction <= 0 || fraction >= 0.95) return null;

  return { trackTitle: savedTrack.title, positionMs: progress.positionMs };
}

export function isBookFullyListened(
  tracks: Track[],
  progress: Pick<AudiobookProgress, "trackId" | "positionMs"> | null | undefined,
): boolean {
  if (tracks.length === 0 || !progress) return false;
  const savedTrack = tracks.find((item) => item.id === progress.trackId);
  return tracks.every(
    (track) =>
      track.sortOrder < (savedTrack?.sortOrder ?? Infinity) ||
      (track.id === progress.trackId && progress.positionMs >= (track.durationMs ?? 0) * 0.95),
  );
}

export function resolveChapterTrackState(
  track: Track,
  progressFraction: number | null | undefined,
): {
  listenProgress: number | null;
  listenPercent: number | null;
} {
  if (progressFraction == null || progressFraction <= 0) {
    return { listenProgress: null, listenPercent: null };
  }
  if (progressFraction >= 0.95) {
    return { listenProgress: 1, listenPercent: 100 };
  }
  return {
    listenProgress: progressFraction,
    listenPercent: Math.max(1, Math.round(progressFraction * 100)),
  };
}

export function buildBookTrackProgress(
  tracks: Track[],
  savedTrackId: string | null | undefined,
  savedPositionMs: number,
  activeTrackId: string | null,
  livePositionMs: number,
): Map<string, number> {
  const sortedTracks = [...tracks].sort((a, b) => a.sortOrder - b.sortOrder);
  const map = new Map<string, number>();
  const progressTrack = sortedTracks.find((track) => track.id === savedTrackId);

  for (const track of sortedTracks) {
    if (track.id === activeTrackId && track.durationMs) {
      map.set(track.id, livePositionMs / track.durationMs);
      continue;
    }
    if (track.id === savedTrackId && track.durationMs) {
      map.set(track.id, savedPositionMs / track.durationMs);
      continue;
    }
    if (progressTrack && track.sortOrder < progressTrack.sortOrder) {
      map.set(track.id, 1);
    }
  }

  return map;
}
