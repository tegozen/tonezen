import type { Track } from "@shared/types";

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
