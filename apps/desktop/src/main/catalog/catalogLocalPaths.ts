import fs from "node:fs";
import path from "node:path";
import {
  isSafeStorageId,
  resolveTrackDownloadPath,
  sanitizeLocalAudioPath,
} from "@core/platform/safeLocalPaths.js";
import type { Track } from "@core/types.js";
import { getDb } from "../db/connection.js";
import { mapTrackRow, type TrackRow } from "../db/mappers.js";

export type CatalogLocalPathsDeps = {
  getTracks: (bookId: string) => Track[];
  getTrackById: (trackId: string) => Track | null;
};

function trackKey(bookId: string, trackId: string): string {
  return `${bookId}\0${trackId}`;
}

function scanDownloadedFilesOnDisk(downloadsRoot: string): Map<string, string> {
  const result = new Map<string, string>();
  if (!fs.existsSync(downloadsRoot)) return result;
  for (const bookEntry of fs.readdirSync(downloadsRoot, { withFileTypes: true })) {
    if (!bookEntry.isDirectory()) continue;
    const bookId = bookEntry.name;
    if (!isSafeStorageId(bookId)) continue;
    const bookDir = path.join(downloadsRoot, bookId);
    for (const fileEntry of fs.readdirSync(bookDir, { withFileTypes: true })) {
      if (!fileEntry.isFile()) continue;
      if (!fileEntry.name.endsWith(".mp3")) continue;
      const trackId = fileEntry.name.slice(0, -4);
      if (!isSafeStorageId(trackId)) continue;
      const fullPath = path.join(bookDir, fileEntry.name);
      const safePath = sanitizeLocalAudioPath(fullPath, [downloadsRoot]);
      if (safePath && fs.existsSync(safePath) && fs.statSync(safePath).size > 0) {
        result.set(trackKey(bookId, trackId), safePath);
      }
    }
  }
  return result;
}

function findOnDiskTrackPath(trackId: string, downloadsRoot: string): [string, string] | null {
  for (const [key, filePath] of scanDownloadedFilesOnDisk(downloadsRoot)) {
    const separator = key.indexOf("\0");
    if (separator < 0) continue;
    const bookId = key.slice(0, separator);
    const id = key.slice(separator + 1);
    if (id === trackId) return [bookId, filePath];
  }
  return null;
}

export function createCatalogLocalPaths(deps: CatalogLocalPathsDeps) {
  const methods = {
    setTrackLocalPath(trackId: string, localPath: string | null, downloadsRoot?: string): void {
      if (localPath == null) {
        getDb()
          .prepare(`UPDATE tracks SET local_path = NULL, local_downloaded_at = NULL WHERE id = ?`)
          .run(trackId);
        return;
      }
      const safePath =
        downloadsRoot != null ? sanitizeLocalAudioPath(localPath, [downloadsRoot]) : null;
      if (!safePath) {
        getDb()
          .prepare(`UPDATE tracks SET local_path = NULL, local_downloaded_at = NULL WHERE id = ?`)
          .run(trackId);
        return;
      }
      getDb()
        .prepare(`UPDATE tracks SET local_path = ?, local_downloaded_at = ? WHERE id = ?`)
        .run(safePath, Date.now(), trackId);
    },

    markTrackDownloaded(
      bookId: string,
      trackId: string,
      localPath: string,
      downloadsRoot: string,
    ): boolean {
      const safePath = sanitizeLocalAudioPath(localPath, [downloadsRoot]);
      if (!safePath || !fs.existsSync(safePath) || fs.statSync(safePath).size <= 0) return false;
      const track =
        deps.getTracks(bookId).find((item) => item.id === trackId) ?? deps.getTrackById(trackId);
      if (!track) return false;
      getDb()
        .prepare(`UPDATE tracks SET local_path = ?, local_downloaded_at = ? WHERE id = ?`)
        .run(safePath, Date.now(), trackId);
      return true;
    },

    resolveLocalTrackPath(bookId: string, trackId: string, downloadsRoot: string): string | null {
      const direct = methods.resolveLocalTrackPathForBook(bookId, trackId, downloadsRoot);
      if (direct) return direct;
      const onDisk = findOnDiskTrackPath(trackId, downloadsRoot);
      if (!onDisk) return null;
      const [diskBookId, path] = onDisk;
      methods.markTrackDownloaded(diskBookId, trackId, path, downloadsRoot);
      return path;
    },

    resolveLocalTrackPathForBook(
      bookId: string,
      trackId: string,
      downloadsRoot: string,
    ): string | null {
      const track = deps.getTracks(bookId).find((item) => item.id === trackId);
      if (track?.localPath) {
        const safePath = sanitizeLocalAudioPath(track.localPath, [downloadsRoot]);
        if (safePath && fs.existsSync(safePath) && fs.statSync(safePath).size > 0) {
          return safePath;
        }
      }
      const onDisk = resolveTrackDownloadPath(downloadsRoot, bookId, trackId);
      if (onDisk && fs.existsSync(onDisk) && fs.statSync(onDisk).size > 0) {
        methods.markTrackDownloaded(bookId, trackId, onDisk, downloadsRoot);
        return onDisk;
      }
      if (track?.localPath) {
        methods.setTrackLocalPath(trackId, null);
      }
      return null;
    },

    reconcileLocalDownloadPaths(downloadsRoot: string): void {
      const onDiskByKey = scanDownloadedFilesOnDisk(downloadsRoot);
      const rows = getDb()
        .prepare(
          `SELECT id, book_id, sort_order, title, filename, artist, duration_ms, local_path, local_downloaded_at, waveform_peaks_json
           FROM tracks`,
        )
        .all() as TrackRow[];

      for (const row of rows) {
        const track = mapTrackRow(row);
        const diskPath = onDiskByKey.get(trackKey(track.bookId, track.id));
        const safeStored = track.localPath
          ? sanitizeLocalAudioPath(track.localPath, [downloadsRoot])
          : null;
        const storedValid =
          safeStored != null && fs.existsSync(safeStored) && fs.statSync(safeStored).size > 0;

        if (!storedValid && diskPath) {
          methods.markTrackDownloaded(track.bookId, track.id, diskPath, downloadsRoot);
          continue;
        }
        if (storedValid) continue;
        if (track.localPath) {
          methods.setTrackLocalPath(track.id, null);
        }
      }
    },

    clearAllLocalPaths(): void {
      getDb().prepare(`UPDATE tracks SET local_path = NULL, local_downloaded_at = NULL`).run();
    },
  };

  return methods;
}
