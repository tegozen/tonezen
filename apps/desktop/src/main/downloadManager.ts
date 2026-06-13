import fs from "node:fs";
import path from "node:path";
import { pipeline } from "node:stream/promises";
import {
  assertAllowedDownloadUrl,
  resolveTrackDownloadPath,
  sanitizeLocalAudioPath,
} from "../shared/safeLocalPaths.js";
import { rmWithRetry, unlinkWithRetry } from "../shared/fileDeleteRetry.js";
import { apiV1Url } from "../shared/serverPaths.js";
import type { Track } from "../shared/types.js";
import { LocalDatabase } from "./database.js";

export class DownloadManager {
  constructor(
    private downloadsRoot: string,
    private baseUrl: string,
    private getAccessToken: () => string | null,
  ) {
    fs.mkdirSync(downloadsRoot, { recursive: true });
  }

  async downloadTrack(bookId: string, trackId: string): Promise<string> {
    const token = this.getAccessToken();
    if (!token) throw new Error("__download_auth_required__");

    const response = await fetch(apiV1Url(this.baseUrl, "/downloads/sign"), {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ track_ids: [trackId] }),
    });
    if (!response.ok) throw new Error("__download_sign_failed__");
    const json = (await response.json()) as { urls: Array<{ track_id: string; url: string }> };
    const signed = json.urls.find((u) => u.track_id === trackId);
    if (!signed) throw new Error("__download_no_signed_url__");

    const targetPath = resolveTrackDownloadPath(this.downloadsRoot, bookId, trackId);
    if (!targetPath) throw new Error("__download_invalid_path__");
    fs.mkdirSync(path.dirname(targetPath), { recursive: true });

    assertAllowedDownloadUrl(signed.url, this.baseUrl);
    const fileRes = await fetch(signed.url);
    if (!fileRes.ok || !fileRes.body) throw new Error("__download_transfer_failed__");
    await pipeline(fileRes.body as unknown as NodeJS.ReadableStream, fs.createWriteStream(targetPath));

    LocalDatabase.setTrackLocalPath(trackId, targetPath);
    return targetPath;
  }

  async deleteLocalTrack(bookId: string, trackId: string): Promise<void> {
    const filePath = resolveTrackDownloadPath(this.downloadsRoot, bookId, trackId);
    if (!filePath) throw new Error("__download_invalid_path__");
    if (fs.existsSync(filePath)) await unlinkWithRetry(filePath);
    LocalDatabase.setTrackLocalPath(trackId, null);
  }

  async deleteAll(): Promise<void> {
    if (fs.existsSync(this.downloadsRoot)) {
      await rmWithRetry(this.downloadsRoot);
      fs.mkdirSync(this.downloadsRoot, { recursive: true });
    }
    LocalDatabase.clearAllLocalPaths();
  }

  getStorageStats(): { usedBytes: number } {
    let usedBytes = 0;
    const walk = (dir: string) => {
      if (!fs.existsSync(dir)) return;
      for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) walk(full);
        else usedBytes += fs.statSync(full).size;
      }
    };
    walk(this.downloadsRoot);
    return { usedBytes };
  }

  listDownloadSummaries(): Array<{
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
            ? sanitizeLocalAudioPath(track.localPath, [this.downloadsRoot])
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
}
