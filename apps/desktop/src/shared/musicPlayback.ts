export const MUSIC_RESTART_THRESHOLD_MS = 3000;

export function shouldRestartCurrentMusicTrack(positionMs: number): boolean {
  return positionMs > MUSIC_RESTART_THRESHOLD_MS;
}

export type MusicSessionState = "Unauthenticated" | "AuthenticatedOnline" | "AuthenticatedOffline";

export function isMusicTrackPlayable(track: { isDownloaded: boolean }, sessionState: MusicSessionState): boolean {
  if (track.isDownloaded) return true;
  return sessionState === "AuthenticatedOnline";
}

export function findFirstPlayableMusicTrack<T extends { isDownloaded: boolean }>(
  tracks: T[],
  sessionState: MusicSessionState,
): T | null {
  return tracks.find((track) => isMusicTrackPlayable(track, sessionState)) ?? null;
}

export function findNextPlayableIndex<T>(
  items: T[],
  currentIndex: number,
  isPlayable: (item: T) => boolean,
  nextIndex: (current: number, size: number) => number,
): number | null {
  if (items.length === 0 || currentIndex < 0 || currentIndex >= items.length) return null;
  let index = currentIndex;
  for (let step = 0; step < items.length - 1; step += 1) {
    index = nextIndex(index, items.length);
    if (index < 0) return null;
    if (isPlayable(items[index])) return index;
  }
  return null;
}

export function findPreviousPlayableIndex<T>(
  items: T[],
  currentIndex: number,
  isPlayable: (item: T) => boolean,
  previousIndex: (current: number, size: number) => number,
): number | null {
  if (items.length === 0 || currentIndex < 0 || currentIndex >= items.length) return null;
  let index = currentIndex;
  for (let step = 0; step < items.length - 1; step += 1) {
    index = previousIndex(index, items.length);
    if (index < 0) return null;
    if (isPlayable(items[index])) return index;
  }
  return null;
}

export function findActiveMusicTrack<T extends { trackId: string }>(
  visibleQueue: T[],
  fullQueue: T[],
  activeTrackId: string | null | undefined,
): T | undefined {
  if (!activeTrackId) return undefined;
  return (
    visibleQueue.find((track) => track.trackId === activeTrackId) ??
    fullQueue.find((track) => track.trackId === activeTrackId)
  );
}
