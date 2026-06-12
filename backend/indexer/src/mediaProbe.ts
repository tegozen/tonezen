import { createHash } from "node:crypto";
import { createReadStream } from "node:fs";
import { stat } from "node:fs/promises";
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
    const json = JSON.parse(stdout) as { format?: { tags?: Record<string, string> } };
    const tags = json.format?.tags ?? {};
    return {
      title: pickTag(tags, "title"),
      artist: pickTag(tags, "artist", "album_artist", "albumartist"),
      album: pickTag(tags, "album"),
      trackNumber: parseTrackNumber(pickTag(tags, "track", "tracknumber", "trck")),
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

export async function analyzeAudioFile(
  contentRoot: string,
  storagePath: string,
): Promise<FileMetadata | null> {
  const filePath = path.join(contentRoot, storagePath);
  try {
    const info = await stat(filePath);
    const checksum = await sha256File(filePath);
    const durationMs = await probeDurationMs(filePath);
    return { sizeBytes: info.size, checksum, durationMs };
  } catch {
    return null;
  }
}
