import fs from "node:fs";
import path from "node:path";
import { rmWithRetry, unlinkWithRetry } from "@core/platform/fileDeleteRetry.js";
import {
  resolveTrackDownloadPath,
  resolveTrackPartPath,
  sanitizeLocalAudioPath,
} from "@core/platform/safeLocalPaths.js";
import type { Track } from "@core/types.js";
import { LocalDatabase } from "../db/localDatabase.js";

export async function deleteLocalTrack(
  downloadsRoot: string,
  bookId: string,
  trackId: string,
): Promise<void> {
  const filePath = resolveTrackDownloadPath(downloadsRoot, bookId, trackId);
  const partPath = resolveTrackPartPath(downloadsRoot, bookId, trackId);
  if (!filePath) throw new Error("__download_invalid_path__");
  if (fs.existsSync(filePath)) await unlinkWithRetry(filePath);
  if (partPath && fs.existsSync(partPath)) await unlinkWithRetry(partPath);
  LocalDatabase.setTrackLocalPath(trackId, null);
}

export async function deleteAllDownloads(downloadsRoot: string): Promise<void> {
  if (fs.existsSync(downloadsRoot)) {
    await rmWithRetry(downloadsRoot);
    fs.mkdirSync(downloadsRoot, { recursive: true });
  }
  LocalDatabase.clearAllLocalPaths();
}

export function getDownloadStorageStats(downloadsRoot: string): { usedBytes: number } {
  let usedBytes = 0;
  const walk = (dir: string) => {
    if (!fs.existsSync(dir)) return;
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else usedBytes += fs.statSync(full).size;
    }
  };
  walk(downloadsRoot);
  return { usedBytes };
}

export function listDownloadSummaries(downloadsRoot: string): Array<{
  bookId: string;
  title: string;
  author?: string;
  contentType: string;
  downloadedTracks: number;
  totalTracks: number;
  sizeBytes: number;
  downloadProgress: number;
}> {
  const books = LocalDatabase.getBooks();
  const tracksByBookId = new Map<string, Track[]>();
  for (const track of LocalDatabase.getAllTracks()) {
    const list = tracksByBookId.get(track.bookId);
    if (list) list.push(track);
    else tracksByBookId.set(track.bookId, [track]);
  }

  return books
    .map((book) => {
      const tracks = tracksByBookId.get(book.id) ?? [];
      const downloaded = tracks.filter((t) => t.localPath);
      if (downloaded.length === 0) return null;
      const sizeBytes = downloaded.reduce((sum, track) => {
        const safePath = track.localPath
          ? sanitizeLocalAudioPath(track.localPath, [downloadsRoot])
          : null;
        if (!safePath || !fs.existsSync(safePath)) return sum;
        return sum + fs.statSync(safePath).size;
      }, 0);
      return {
        bookId: book.id,
        title: book.title,
        author: book.author,
        contentType: book.contentType,
        downloadedTracks: downloaded.length,
        totalTracks: tracks.length,
        sizeBytes,
        downloadProgress: downloaded.length / Math.max(tracks.length, 1),
      };
    })
    .filter((item): item is NonNullable<typeof item> => item !== null);
}
