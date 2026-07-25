import { createHash } from "node:crypto";
import { createReadStream } from "node:fs";
import { readdir, stat } from "node:fs/promises";
import { execFile, spawn } from "node:child_process";
import { promisify } from "node:util";
import path from "node:path";

const execFileAsync = promisify(execFile);
export const WAVEFORM_PEAK_COUNT = 64;
const WAVEFORM_SAMPLE_RATE = 8000;
const FFPROBE_TIMEOUT_MS = 60_000;
const FFMPEG_TIMEOUT_MS = 120_000;

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

interface WaveformBucket {
  sumSquares: number;
  samples: number;
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

function pickTag(tags: Record<string, string>, ...keys: string[]): string | null {
  const normalized = new Map<string, string>();
  for (const [key, value] of Object.entries(tags)) {
    if (typeof value === "string" && value.trim()) {
      normalized.set(key.toLowerCase(), value.trim());
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

export async function resolveStorageObjectPath(objectPath: string): Promise<string | null> {
  try {
    const info = await stat(objectPath);
    if (info.isFile()) return objectPath;
    if (!info.isDirectory()) return null;
  } catch {
    return null;
  }

  const entries = await readdir(objectPath, { withFileTypes: true });
  let best: { path: string; size: number } | null = null;

  for (const entry of entries) {
    if (!entry.isFile() || entry.name.endsWith(".json")) continue;
    const filePath = path.join(objectPath, entry.name);
    const fileInfo = await stat(filePath);
    if (fileInfo.size === 0) continue;
    if (!best || fileInfo.size > best.size) {
      best = { path: filePath, size: fileInfo.size };
    }
  }

  return best?.path ?? null;
}

export async function probeAudioTags(filePath: string): Promise<AudioTags | null> {
  try {
    const { stdout } = await execFileAsync(
      "ffprobe",
      ["-v", "quiet", "-print_format", "json", "-show_format", filePath],
      { timeout: FFPROBE_TIMEOUT_MS, maxBuffer: 2 * 1024 * 1024 },
    );
    const json = JSON.parse(stdout) as {
      format?: { tags?: Record<string, string>; duration?: string };
    };
    const tags = json.format?.tags ?? {};
    const durationRaw = json.format?.duration;
    const durationSeconds = durationRaw != null ? parseFloat(durationRaw) : Number.NaN;
    const durationMs = Number.isFinite(durationSeconds)
      ? Math.round(durationSeconds * 1000)
      : null;
    return {
      title: pickTag(tags, "title"),
      artist: pickTag(tags, "artist", "album_artist", "albumartist"),
      album: pickTag(tags, "album"),
      trackNumber: parseTrackNumber(pickTag(tags, "track", "tracknumber", "trck")),
      durationMs,
    };
  } catch {
    return null;
  }
}

export async function sha256File(filePath: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const hash = createHash("sha256");
    const stream = createReadStream(filePath);
    stream.on("error", reject);
    stream.on("data", (chunk) => hash.update(chunk));
    stream.on("end", () => resolve(hash.digest("hex")));
  });
}

export async function probeDurationMs(filePath: string): Promise<number | null> {
  try {
    const { stdout } = await execFileAsync(
      "ffprobe",
      [
        "-v",
        "error",
        "-show_entries",
        "format=duration",
        "-of",
        "default=noprint_wrappers=1:nokey=1",
        filePath,
      ],
      { timeout: FFPROBE_TIMEOUT_MS, maxBuffer: 64 * 1024 },
    );
    const seconds = parseFloat(stdout.trim());
    if (Number.isNaN(seconds)) return null;
    return Math.round(seconds * 1000);
  } catch {
    return null;
  }
}

export async function probeWaveformPeaks(
  filePath: string,
  durationMs: number | null,
  bucketCount = WAVEFORM_PEAK_COUNT,
): Promise<number[] | null> {
  if (durationMs == null || durationMs <= 0 || bucketCount <= 0) return null;
  const totalSamples = Math.max(1, Math.round((durationMs / 1000) * WAVEFORM_SAMPLE_RATE));
  const buckets = Array.from({ length: bucketCount }, () => ({ sumSquares: 0, samples: 0 }));

  return new Promise((resolve) => {
    let settled = false;
    let decodedSamples = 0;
    let remainder: Buffer<ArrayBufferLike> = Buffer.alloc(0);
    const ffmpeg = spawn("ffmpeg", [
      "-v",
      "error",
      "-i",
      filePath,
      "-ac",
      "1",
      "-ar",
      String(WAVEFORM_SAMPLE_RATE),
      "-f",
      "s16le",
      "pipe:1",
    ]);

    const finish = (result: number[] | null) => {
      if (settled) return;
      settled = true;
      clearTimeout(killTimer);
      resolve(result);
    };
    const killTimer = setTimeout(() => {
      ffmpeg.kill("SIGKILL");
      finish(null);
    }, FFMPEG_TIMEOUT_MS);

    ffmpeg.stdout.on("data", (chunk: Buffer<ArrayBufferLike>) => {
      const input = remainder.length > 0 ? Buffer.concat([remainder, chunk]) : chunk;
      const evenLength = input.length - (input.length % 2);
      for (let offset = 0; offset + 1 < evenLength; offset += 2) {
        const sample = input.readInt16LE(offset) / 32768;
        const bucketIndex = Math.min(
          bucketCount - 1,
          Math.floor((decodedSamples * bucketCount) / totalSamples),
        );
        buckets[bucketIndex].sumSquares += sample * sample;
        buckets[bucketIndex].samples += 1;
        decodedSamples += 1;
      }
      remainder = evenLength < input.length ? input.subarray(evenLength) : Buffer.alloc(0);
    });

    ffmpeg.on("error", () => finish(null));
    ffmpeg.on("close", (code) => {
      if (code !== 0 || decodedSamples === 0) {
        finish(null);
        return;
      }
      finish(normalizeWaveformBuckets(buckets));
    });
  });
}

export async function analyzeAudioFileAtPath(
  filePath: string,
  options?: { knownDurationMs?: number | null },
): Promise<FileMetadata | null> {
  try {
    const info = await stat(filePath);
    const checksum = await sha256File(filePath);
    const durationMs =
      typeof options?.knownDurationMs === "number"
        ? options.knownDurationMs
        : await probeDurationMs(filePath);
    const waveformPeaks = await probeWaveformPeaks(filePath, durationMs);
    return { sizeBytes: info.size, checksum, durationMs, waveformPeaks };
  } catch {
    return null;
  }
}

export async function analyzeAudioFile(
  contentRoot: string,
  storagePath: string,
  options?: { knownDurationMs?: number | null },
): Promise<FileMetadata | null> {
  const filePath = await resolveStorageObjectPath(path.join(contentRoot, storagePath));
  if (!filePath) return null;
  return analyzeAudioFileAtPath(filePath, options);
}
