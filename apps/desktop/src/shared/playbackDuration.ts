export function effectiveDurationMs(
  audioDurationSec: number,
  trackDurationMs?: number,
): number {
  const fromAudio =
    Number.isFinite(audioDurationSec) && audioDurationSec > 0
      ? Math.floor(audioDurationSec * 1000)
      : 0;
  const fromTrack = trackDurationMs ?? 0;
  return Math.max(fromAudio, fromTrack);
}
