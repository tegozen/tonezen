import { createHash } from "node:crypto";
import { createReadStream } from "node:fs";
import { readdir, stat } from "node:fs/promises";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import path from "node:path";

const execFileAsync = promisify(execFile);

export interface FileMetadata {
  sizeBytes: number;
  checksum: string;
  durationMs: number | null;
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
}

/** Reuse DB metadata when file size is unchanged (avoids sha256/ffprobe on rescans). */
export function metadataFromStoredIfUnchanged(
  stored: StoredFileMetadata,
  fileSizeBytes: number,
): FileMetadata | null {
  if (!stored.checksum || stored.size_bytes == null) return null;
  if (Number(stored.size_bytes) !== fileSizeBytes) return null;
  return {
    sizeBytes: fileSizeBytes,
    checksum: stored.checksum,
    durationMs: stored.duration_ms,
  };
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
    const { stdout } = await execFileAsync("ffprobe", [
      "-v",
      "quiet",
      "-print_format",
      "json",
      "-show_format",
      filePath,
    ]);
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
    const { stdout } = await execFileAsync("ffprobe", [
      "-v",
      "error",
      "-show_entries",
      "format=duration",
      "-of",
      "default=noprint_wrappers=1:nokey=1",
      filePath,
    ]);
    const seconds = parseFloat(stdout.trim());
    if (Number.isNaN(seconds)) return null;
    return Math.round(seconds * 1000);
  } catch {
    return null;
  }
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
    return { sizeBytes: info.size, checksum, durationMs };
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
