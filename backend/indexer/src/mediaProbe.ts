import { createHash } from "node:crypto";
import { createReadStream } from "node:fs";
import { readdir, stat } from "node:fs/promises";
import { execFile, spawn } from "node:child_process";
import { promisify } from "node:util";
import path from "node:path";
import {
  WAVEFORM_PEAK_COUNT,
  normalizeWaveformBuckets,
  parseTrackNumber,
  pickTag,
  type AudioTags,
  type FileMetadata,
} from "./mediaProbePure.js";

export {
  WAVEFORM_PEAK_COUNT,
  metadataFromStoredIfUnchanged,
  isValidWaveformPeaks,
  normalizeWaveformBuckets,
  waveformPeaksFromPcm16,
  parseTrackNumber,
  type FileMetadata,
  type AudioTags,
  type StoredFileMetadata,
} from "./mediaProbePure.js";

const execFileAsync = promisify(execFile);
const WAVEFORM_SAMPLE_RATE = 8000;
const FFPROBE_TIMEOUT_MS = 60_000;
const FFMPEG_TIMEOUT_MS = 120_000;

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
