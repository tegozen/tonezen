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
