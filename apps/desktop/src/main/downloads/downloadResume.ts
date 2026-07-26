import fs from "node:fs";
import { createWriteStream } from "node:fs";
import { open, rename, stat } from "node:fs/promises";
import path from "node:path";
import { pipeline } from "node:stream/promises";
import { Readable } from "node:stream";
import { progressFraction, resolveResumeAction } from "@core/downloads/downloadResumePolicy.js";
import { unlinkWithRetry } from "@core/platform/fileDeleteRetry.js";
import {
  assertAllowedDownloadUrl,
  resolveTrackDownloadPath,
  resolveTrackPartPath,
} from "@core/platform/safeLocalPaths.js";
import { resolveDownloadUrl, signedUrlForTrack } from "./downloadUrl.js";

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

export interface DownloadResumeContext {
  downloadsRoot: string;
  baseUrl: string;
  getAccessToken: () => string | null;
  setActiveAbort: (abort: AbortController | null) => void;
  clearActiveAbortIf: (abort: AbortController) => void;
}

export async function downloadTrackResumable(
  ctx: DownloadResumeContext,
  bookId: string,
  trackId: string,
  bytesAlreadyDownloaded: number,
  totalBytesHint: number | null,
  onProgress: (progress: number) => void,
  isCancelled: () => boolean,
): Promise<ResumableDownloadOutcome> {
  const partPath = resolveTrackPartPath(ctx.downloadsRoot, bookId, trackId);
  const finalPath = resolveTrackDownloadPath(ctx.downloadsRoot, bookId, trackId);
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

  const signedUrl = await signedUrlForTrack(ctx.baseUrl, ctx.getAccessToken, trackId);
  const url = resolveDownloadUrl(ctx.baseUrl, signedUrl);
  assertAllowedDownloadUrl(url, ctx.baseUrl);

  let totalBytes = totalBytesHint;
  let attemptOffset = offset;

  for (let attempt = 0; attempt < 2; attempt++) {
    if (isCancelled()) throw new DownloadCancelledError();

    const headers: Record<string, string> = {};
    if (attemptOffset > 0) headers.Range = `bytes=${attemptOffset}-`;

    const abort = new AbortController();
    ctx.setActiveAbort(abort);

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
        throw new Error(`__download_transfer_failed__:${response.status}`);
      }

      const body = response.body;
      if (!body) throw new Error("__download_transfer_failed__:empty_body");

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
      ctx.clearActiveAbortIf(abort);
    }
  }

  throw new Error("__download_transfer_failed__");
}
