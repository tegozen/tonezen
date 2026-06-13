import fs from "node:fs";
import path from "node:path";
import { pipeline } from "node:stream/promises";
import {
  assertAllowedDownloadUrl,
  resolveTrackDownloadPath,
  sanitizeLocalAudioPath,
} from "../shared/safeLocalPaths.js";
import { apiV1Url } from "../shared/serverPaths.js";
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
    if (!token) throw new Error("Authentication required for downloads");

    const response = await fetch(apiV1Url(this.baseUrl, "/downloads/sign"), {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ track_ids: [trackId] }),
    });
    if (!response.ok) throw new Error(`Sign failed: ${response.status}`);
    const json = (await response.json()) as { urls: Array<{ track_id: string; url: string }> };
    const signed = json.urls.find((u) => u.track_id === trackId);
    if (!signed) throw new Error("No signed URL returned");

    const targetPath = resolveTrackDownloadPath(this.downloadsRoot, bookId, trackId);
    if (!targetPath) throw new Error("Invalid download path");
    fs.mkdirSync(path.dirname(targetPath), { recursive: true });

    assertAllowedDownloadUrl(signed.url, this.baseUrl);
    const fileRes = await fetch(signed.url);
    if (!fileRes.ok || !fileRes.body) throw new Error(`Download failed: ${fileRes.status}`);
    await pipeline(fileRes.body as unknown as NodeJS.ReadableStream, fs.createWriteStream(targetPath));

    LocalDatabase.setTrackLocalPath(trackId, targetPath);
    return targetPath;
  }

  deleteLocalTrack(bookId: string, trackId: string): void {
    const filePath = resolveTrackDownloadPath(this.downloadsRoot, bookId, trackId);
    if (!filePath) throw new Error("Invalid download path");
    if (fs.existsSync(filePath)) fs.unlinkSync(filePath);
    LocalDatabase.setTrackLocalPath(trackId, null);
  }

  deleteAll(): void {
    if (fs.existsSync(this.downloadsRoot)) {
      fs.rmSync(this.downloadsRoot, { recursive: true, force: true });
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
    return books
      .map((book) => {
        const tracks = LocalDatabase.getTracks(book.id);
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
