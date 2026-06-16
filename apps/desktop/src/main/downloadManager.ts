import fs from "node:fs";
import { createWriteStream } from "node:fs";
import { open, rename, stat } from "node:fs/promises";
import path from "node:path";
import { pipeline } from "node:stream/promises";
import { Readable } from "node:stream";
import { progressFraction, resolveResumeAction } from "../shared/downloadResumePolicy.js";
import { rmWithRetry, unlinkWithRetry } from "../shared/fileDeleteRetry.js";
import {
  assertAllowedDownloadUrl,
  normalizeDownloadUrl,
  resolveTrackDownloadPath,
  resolveTrackPartPath,
  sanitizeLocalAudioPath,
} from "../shared/safeLocalPaths.js";
import { apiV1Url } from "../shared/serverPaths.js";
import type { Track } from "../shared/types.js";
import { LocalDatabase } from "./database.js";

export class DownloadCancelledError extends Error {
  constructor() {
    super("Download cancelled");
    this.name = "DownloadCancelledError";
  }
}

export interface ResumableDownloadOutcome {
  finalPath: string;
  bytesDownloaded: number;
  totalBytes: number | null;
}

export class DownloadManager {
  private activeAbort: AbortController | null = null;

  constructor(
    private downloadsRoot: string,
    private baseUrl: string,
    private getAccessToken: () => string | null,
  ) {
    fs.mkdirSync(downloadsRoot, { recursive: true });
  }

  cancelActiveDownload(): void {
    this.activeAbort?.abort();
    this.activeAbort = null;
  }

  async downloadTrack(bookId: string, trackId: string): Promise<string> {
    const outcome = await this.downloadTrackResumable(
      bookId,
      trackId,
      0,
      null,
      () => {},
      () => false,
    );
    LocalDatabase.setTrackLocalPath(trackId, outcome.finalPath);
    return outcome.finalPath;
  }

  async downloadTrackResumable(
    bookId: string,
    trackId: string,
    bytesAlreadyDownloaded: number,
    totalBytesHint: number | null,
    onProgress: (progress: number) => void,
    isCancelled: () => boolean,
  ): Promise<ResumableDownloadOutcome> {
    const partPath = resolveTrackPartPath(this.downloadsRoot, bookId, trackId);
    const finalPath = resolveTrackDownloadPath(this.downloadsRoot, bookId, trackId);
    if (!partPath || !finalPath) throw new Error("__download_invalid_path__");

    fs.mkdirSync(path.dirname(partPath), { recursive: true });

    let offset = Math.max(bytesAlreadyDownloaded, 0);
    if (offset === 0) {
      if (fs.existsSync(partPath)) await unlinkWithRetry(partPath);
    } else if (!fs.existsSync(partPath)) {
      offset = 0;
    } else {
      offset = (await stat(partPath)).size;
    }

    const signedUrl = await this.signedUrlForTrack(trackId);
    const url = this.resolveDownloadUrl(signedUrl);
    assertAllowedDownloadUrl(url, this.baseUrl);

    let totalBytes = totalBytesHint;
    let attemptOffset = offset;

    for (let attempt = 0; attempt < 2; attempt++) {
      if (isCancelled()) throw new DownloadCancelledError();

      const headers: Record<string, string> = {};
      if (attemptOffset > 0) headers.Range = `bytes=${attemptOffset}-`;

      const abort = new AbortController();
      this.activeAbort = abort;

      try {
        const response = await fetch(url, { headers, signal: abort.signal });
        const partLength = fs.existsSync(partPath) ? (await stat(partPath)).size : 0;
        const action = resolveResumeAction(
          partLength,
          attemptOffset,
          totalBytes,
          attemptOffset > 0 ? response.status : null,
        );

        if (action === "RESTART") {
          if (fs.existsSync(partPath)) await unlinkWithRetry(partPath);
          attemptOffset = 0;
          continue;
        }

        if (!response.ok && response.status !== 206) {
          throw new Error("__download_transfer_failed__");
        }

        const body = response.body;
        if (!body) throw new Error("__download_transfer_failed__");

        const contentLength = Number(response.headers.get("content-length") ?? -1);
        if (response.status === 206) {
          totalBytes = attemptOffset + Math.max(contentLength, 0);
        } else if (contentLength > 0) {
          totalBytes = contentLength;
        }

        const append =
          attemptOffset > 0 &&
          fs.existsSync(partPath) &&
          (await stat(partPath)).size === attemptOffset;

        if (!append && fs.existsSync(partPath)) {
          await unlinkWithRetry(partPath);
        }

        let downloaded = append ? attemptOffset : 0;
        let lastBucket = -1;
        const nodeStream = Readable.fromWeb(body as import("node:stream/web").ReadableStream);
        const fileHandle = await open(partPath, append ? "a" : "w");

        try {
          for await (const chunk of nodeStream) {
            if (isCancelled()) throw new DownloadCancelledError();
            const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
            await fileHandle.write(buffer);
            downloaded += buffer.length;
            const fraction = progressFraction(downloaded, totalBytes);
            if (fraction != null) {
              const bucket = Math.floor(fraction * 50);
              if (bucket > lastBucket) {
                lastBucket = bucket;
                onProgress(fraction);
              }
            }
          }
        } finally {
          await fileHandle.close();
        }

        attemptOffset = downloaded;
        const partSize = (await stat(partPath)).size;
        if (partSize <= 0) throw new Error("__download_transfer_failed__");

        if (fs.existsSync(finalPath)) await unlinkWithRetry(finalPath);
        try {
          await rename(partPath, finalPath);
        } catch {
          await pipeline(fs.createReadStream(partPath), createWriteStream(finalPath));
          await unlinkWithRetry(partPath);
        }

        onProgress(1);
        return {
          finalPath,
          bytesDownloaded: (await stat(finalPath)).size,
          totalBytes,
        };
      } catch (error) {
        if (error instanceof DownloadCancelledError) throw error;
        if (abort.signal.aborted) throw new DownloadCancelledError();
        throw error;
      } finally {
        if (this.activeAbort === abort) this.activeAbort = null;
      }
    }

    throw new Error("__download_transfer_failed__");
  }

  async deleteLocalTrack(bookId: string, trackId: string): Promise<void> {
    const filePath = resolveTrackDownloadPath(this.downloadsRoot, bookId, trackId);
    const partPath = resolveTrackPartPath(this.downloadsRoot, bookId, trackId);
    if (!filePath) throw new Error("__download_invalid_path__");
    if (fs.existsSync(filePath)) await unlinkWithRetry(filePath);
    if (partPath && fs.existsSync(partPath)) await unlinkWithRetry(partPath);
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

  private async signedUrlForTrack(trackId: string): Promise<string> {
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
    return signed.url;
  }

  private resolveDownloadUrl(signedUrl: string): string {
    const apiBase = this.baseUrl.replace(/\/$/, "");
    let absolute = signedUrl;
    if (!signedUrl.startsWith("http://") && !signedUrl.startsWith("https://")) {
      if (signedUrl.startsWith("/storage/v1/")) {
        absolute = `${apiBase}${signedUrl}`;
      } else if (signedUrl.startsWith("/")) {
        absolute = `${apiBase}/storage/v1${signedUrl}`;
      }
    }
    try {
      const target = new URL(absolute);
      const allowed = new URL(apiBase);
      if (target.hostname === "localhost" || target.hostname === "127.0.0.1") {
        const port = target.port || allowed.port;
        target.hostname = allowed.hostname;
        if (port) target.port = port;
        return target.toString();
      }
    } catch {
      return absolute;
    }
    return normalizeDownloadUrl(absolute, apiBase);
  }
}
