import type { AudiobookProgress, Track } from "@core/types";
import { hasMeaningfulAudiobookProgress } from "@core/playback/cycleListenProgress";

export interface BookContinueState {
  trackTitle: string;
  positionMs: number;
}

export interface BookContinuePlayHead {
  track: Track;
  positionMs: number;
}

const COMPLETED_FRACTION = 0.95;

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

/** Where Continue / resume should actually start (advances past a ≥95% chapter). */
export function resolveBookContinuePlayHead(
  tracks: Track[],
  progress: Pick<AudiobookProgress, "trackId" | "positionMs"> | null | undefined,
): BookContinuePlayHead | null {
  const sortedTracks = [...tracks].sort((a, b) => a.sortOrder - b.sortOrder);
  if (sortedTracks.length === 0) return null;
  if (!progress) {
    return { track: sortedTracks[0], positionMs: 0 };
  }
  const index = sortedTracks.findIndex((track) => track.id === progress.trackId);
  if (index < 0) {
    return { track: sortedTracks[0], positionMs: 0 };
  }
  const track = sortedTracks[index];
  const durationMs = track.durationMs ?? 0;
  const isComplete = durationMs > 0 && progress.positionMs >= durationMs * COMPLETED_FRACTION;
  if (!isComplete) {
    return { track, positionMs: Math.max(0, progress.positionMs) };
  }
  if (index < sortedTracks.length - 1) {
    return { track: sortedTracks[index + 1], positionMs: 0 };
  }
  return null;
}

export function canContinueBookListening(
  bookId: string,
  tracks: Track[],
  progress: Pick<AudiobookProgress, "bookId" | "trackId" | "positionMs"> | null | undefined,
): BookContinueState | null {
  if (!bookId || !progress || progress.bookId !== bookId || tracks.length === 0) return null;

  const sortedTracks = [...tracks].sort((a, b) => a.sortOrder - b.sortOrder);
  if (!hasMeaningfulAudiobookProgress(sortedTracks, progress as AudiobookProgress)) return null;
  if (isBookFullyListened(sortedTracks, progress)) return null;

  const head = resolveBookContinuePlayHead(sortedTracks, progress);
  if (!head) return null;
  return { trackTitle: head.track.title, positionMs: head.positionMs };
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
