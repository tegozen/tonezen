import fs from "node:fs";
import { LocalDatabase } from "../db/localDatabase.js";
import {
  deleteAllDownloads,
  deleteLocalTrack as deleteLocalTrackFiles,
  getDownloadStorageStats,
  listDownloadSummaries as buildDownloadSummaries,
} from "./downloadCleanup.js";
import {
  DownloadCancelledError,
  downloadTrackResumable as executeResumableDownload,
  type ResumableDownloadOutcome,
} from "./downloadResume.js";

export { DownloadCancelledError };
export type { ResumableDownloadOutcome };

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
    LocalDatabase.setTrackLocalPath(trackId, outcome.finalPath, this.downloadsRoot);
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
    return executeResumableDownload(
      {
        downloadsRoot: this.downloadsRoot,
        baseUrl: this.baseUrl,
        getAccessToken: this.getAccessToken,
        setActiveAbort: (abort) => {
          this.activeAbort = abort;
        },
        clearActiveAbortIf: (abort) => {
          if (this.activeAbort === abort) this.activeAbort = null;
        },
      },
      bookId,
      trackId,
      bytesAlreadyDownloaded,
      totalBytesHint,
      onProgress,
      isCancelled,
    );
  }

  async deleteLocalTrack(bookId: string, trackId: string): Promise<void> {
    return deleteLocalTrackFiles(this.downloadsRoot, bookId, trackId);
  }

  async deleteAll(): Promise<void> {
    return deleteAllDownloads(this.downloadsRoot);
  }

  getStorageStats(): { usedBytes: number } {
    return getDownloadStorageStats(this.downloadsRoot);
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
    return buildDownloadSummaries(this.downloadsRoot);
  }
}
