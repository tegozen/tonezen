/** HTMLMediaElement.HAVE_METADATA — duration and seek range are known. */
export const MEDIA_HAVE_METADATA = 1;

export function needsMetadataBeforeSeek(startMs: number, readyState: number): boolean {
  return startMs > 0 && readyState < MEDIA_HAVE_METADATA;
}

export function startSecondsFromMs(startMs: number): number {
  return startMs / 1000;
}
