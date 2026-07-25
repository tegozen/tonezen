import type { AudiobookProgress, Track } from "@core/types.js";

export type AudiobookPlaybackIntent =
  | { kind: "Resume"; positionMs: number }
  | { kind: "StartFromZero" }
  | { kind: "ConfirmEarlierChapter"; savedTrackId: string; savedPositionMs: number };

const COMPLETED_FRACTION_THRESHOLD = 0.95;

export function resolveAudiobookPlaybackStartMs(
  progress: AudiobookProgress | null | undefined,
  track: Track,
): number {
  if (!progress || progress.trackId !== track.id) return 0;
  const durationMs = track.durationMs;
  if (durationMs == null || durationMs <= 0) return progress.positionMs;
  if (progress.positionMs >= durationMs * COMPLETED_FRACTION_THRESHOLD) return 0;
  return progress.positionMs;
}

export function resolveAudiobookPlaybackIntent(
  sortedTracks: Track[],
  bookProgress: AudiobookProgress | null | undefined,
  clickedTrack: Track,
): AudiobookPlaybackIntent {
  if (!bookProgress) return { kind: "StartFromZero" };
  const savedIndex = sortedTracks.findIndex((track) => track.id === bookProgress.trackId);
  const clickedIndex = sortedTracks.findIndex((track) => track.id === clickedTrack.id);
  if (savedIndex < 0 || clickedIndex < 0) return { kind: "StartFromZero" };
  if (clickedIndex === savedIndex) {
    return {
      kind: "Resume",
      positionMs: resolveAudiobookPlaybackStartMs(bookProgress, clickedTrack),
    };
  }
  if (clickedIndex > savedIndex) return { kind: "StartFromZero" };
  return {
    kind: "ConfirmEarlierChapter",
    savedTrackId: bookProgress.trackId,
    savedPositionMs: bookProgress.positionMs,
  };
}
