export const MUSIC_RESTART_THRESHOLD_MS = 3000;

export function shouldRestartCurrentMusicTrack(positionMs: number): boolean {
  return positionMs > MUSIC_RESTART_THRESHOLD_MS;
}
