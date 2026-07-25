export const WAVEFORM_PEAK_COUNT = 64;

export function normalizeWaveformPeaks(value: unknown): number[] | null {
  if (
    !Array.isArray(value) ||
    value.length !== WAVEFORM_PEAK_COUNT ||
    !value.every((peak) => Number.isInteger(peak) && peak >= 0 && peak <= 100)
  ) {
    return null;
  }
  return value as number[];
}

export function parseWaveformPeaksJson(raw: string | null | undefined): number[] | null {
  if (!raw) return null;
  try {
    return normalizeWaveformPeaks(JSON.parse(raw));
  } catch {
    return null;
  }
}

export function serializeWaveformPeaks(value: unknown): string | null {
  const peaks = normalizeWaveformPeaks(value);
  return peaks ? JSON.stringify(peaks) : null;
}
