import { repairMojibake } from "./textEncoding.js";

export const WAVEFORM_PEAK_COUNT = 64;

export interface FileMetadata {
  sizeBytes: number;
  checksum: string;
  durationMs: number | null;
  waveformPeaks: number[] | null;
}

export interface AudioTags {
  title: string | null;
  artist: string | null;
  album: string | null;
  trackNumber: number | null;
  durationMs: number | null;
}

export interface StoredFileMetadata {
  checksum: string | null;
  size_bytes: number | null;
  duration_ms: number | null;
  waveform_peaks?: unknown;
}

interface WaveformBucket {
  sumSquares: number;
  samples: number;
}

/** Reuse DB metadata when file size is unchanged (avoids sha256/ffprobe on rescans). */
export function metadataFromStoredIfUnchanged(
  stored: StoredFileMetadata,
  fileSizeBytes: number,
): FileMetadata | null {
  if (!stored.checksum || stored.size_bytes == null) return null;
  if (Number(stored.size_bytes) !== fileSizeBytes) return null;
  if (!isValidWaveformPeaks(stored.waveform_peaks)) return null;
  return {
    sizeBytes: fileSizeBytes,
    checksum: stored.checksum,
    durationMs: stored.duration_ms,
    waveformPeaks: stored.waveform_peaks,
  };
}

export function isValidWaveformPeaks(
  value: unknown,
  count = WAVEFORM_PEAK_COUNT,
): value is number[] {
  return (
    Array.isArray(value) &&
    value.length === count &&
    value.every((peak) => Number.isInteger(peak) && peak >= 0 && peak <= 100)
  );
}

export function normalizeWaveformBuckets(buckets: readonly WaveformBucket[]): number[] | null {
  if (buckets.length === 0) return null;
  const rmsValues = buckets.map((bucket) =>
    bucket.samples > 0 ? Math.sqrt(bucket.sumSquares / bucket.samples) : 0,
  );
  const max = Math.max(...rmsValues);
  if (!Number.isFinite(max)) return null;
  if (max <= 0) return buckets.map(() => 0);
  return rmsValues.map((value) => Math.round((value / max) * 100));
}

export function waveformPeaksFromPcm16(
  input: Buffer,
  totalSamples: number,
  bucketCount = WAVEFORM_PEAK_COUNT,
): number[] | null {
  if (input.length < 2 || totalSamples <= 0 || bucketCount <= 0) return null;
  const buckets = Array.from({ length: bucketCount }, () => ({ sumSquares: 0, samples: 0 }));
  let decodedSamples = 0;

  for (let offset = 0; offset + 1 < input.length; offset += 2) {
    const sample = input.readInt16LE(offset) / 32768;
    const bucketIndex = Math.min(
      bucketCount - 1,
      Math.floor((decodedSamples * bucketCount) / totalSamples),
    );
    buckets[bucketIndex].sumSquares += sample * sample;
    buckets[bucketIndex].samples += 1;
    decodedSamples += 1;
  }

  return decodedSamples > 0 ? normalizeWaveformBuckets(buckets) : null;
}

export function pickTag(tags: Record<string, string>, ...keys: string[]): string | null {
  const normalized = new Map<string, string>();
  for (const [key, value] of Object.entries(tags)) {
    if (typeof value === "string" && value.trim()) {
      normalized.set(key.toLowerCase(), repairMojibake(value));
    }
  }
  for (const key of keys) {
    const value = normalized.get(key.toLowerCase());
    if (value) return value;
  }
  return null;
}

export function parseTrackNumber(raw: string | null | undefined): number | null {
  if (!raw) return null;
  const match = raw.trim().match(/^(\d+)/);
  if (!match) return null;
  const n = parseInt(match[1], 10);
  return Number.isFinite(n) && n > 0 ? n : null;
}
