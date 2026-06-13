const STORAGE_KEY = "tonezen-playback-volume";
const DEFAULT_VOLUME = 1;

export function clampPlaybackVolume(value: number): number {
  if (!Number.isFinite(value)) return DEFAULT_VOLUME;
  return Math.min(1, Math.max(0, value));
}

export function loadPlaybackVolume(): number {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw == null) return DEFAULT_VOLUME;
    return clampPlaybackVolume(Number.parseFloat(raw));
  } catch {
    return DEFAULT_VOLUME;
  }
}

export function savePlaybackVolume(volume: number): void {
  try {
    localStorage.setItem(STORAGE_KEY, String(clampPlaybackVolume(volume)));
  } catch {
    // ignore quota / private mode
  }
}
