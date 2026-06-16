import { COMPLETED_HISTORY_LIMIT } from "./downloadResumePolicy.js";
import type { Book, ContentType, Track } from "./types.js";
import type { DownloadQueueItem, DownloadQueueState } from "./downloadQueueState.js";

export interface CompletedDownloadItem {
  bookId: string;
  trackId: string;
  title: string;
  subtitle: string | null;
  contentType: ContentType | string;
  durationMs?: number;
  completedAt: number;
}

export function activeDownloadItems(downloadQueue: DownloadQueueState): DownloadQueueItem[] {
  return downloadQueue.queuedItems.filter(
    (item) =>
      item.status === "QUEUED" ||
      item.status === "DOWNLOADING" ||
      item.status === "PAUSED_OFFLINE",
  );
}

export function completedDownloadItems(
  downloadQueue: DownloadQueueState,
  tracks: Track[],
  books: Book[],
): CompletedDownloadItem[] {
  const byBookId = new Map(books.map((book) => [book.id, book]));
  const byTrackId = new Map<string, CompletedDownloadItem>();

  tracks
    .filter((track) => track.localPath && track.localDownloadedAt != null)
    .sort((left, right) => (left.localDownloadedAt ?? 0) - (right.localDownloadedAt ?? 0))
    .forEach((track) => {
      const book = byBookId.get(track.bookId);
      byTrackId.set(track.id, {
        bookId: track.bookId,
        trackId: track.id,
        title: track.title,
        subtitle: null,
        contentType: book?.contentType ?? "music",
        durationMs: track.durationMs,
        completedAt: track.localDownloadedAt ?? 0,
      });
    });

  downloadQueue.completedHistory
    .filter((item) => item.status === "COMPLETED")
    .forEach((item) => {
      byTrackId.set(item.trackId, {
        bookId: item.bookId,
        trackId: item.trackId,
        title: item.title,
        subtitle: item.subtitle,
        contentType: item.contentType,
        completedAt: item.completedAt ?? item.enqueuedAt,
      });
    });

  return Array.from(byTrackId.values())
    .sort((left, right) => left.completedAt - right.completedAt)
    .slice(-COMPLETED_HISTORY_LIMIT);
}
