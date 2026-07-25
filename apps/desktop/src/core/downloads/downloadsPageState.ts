import { COMPLETED_HISTORY_LIMIT } from "@core/downloads/downloadResumePolicy.js";
import type { Book, ContentType, Cycle, Track } from "@core/types.js";
import type { DownloadQueueItem, DownloadQueueState } from "@core/downloads/downloadQueueState.js";

export interface CompletedDownloadItem {
  bookId: string;
  trackId: string;
  title: string;
  subtitle: string | null;
  contentType: ContentType | string;
  durationMs?: number;
  completedAt: number;
}

export interface DownloadsPageItem {
  bookId: string;
  trackId: string;
  title: string;
  subtitle: string | null;
  contentType: ContentType | string;
  status: DownloadQueueItem["status"] | "COMPLETED";
  progress: number | null;
  durationMs?: number;
  completedAt: number | null;
}

export interface DownloadsBookGroup {
  bookId: string;
  title: string;
  items: DownloadsPageItem[];
}

export interface DownloadsCycleGroup {
  cycleId: string;
  title: string;
  books: DownloadsBookGroup[];
}

export interface DownloadsPageGroups {
  audiobookCycles: DownloadsCycleGroup[];
  audiobookStandaloneBooks: DownloadsBookGroup[];
  music: DownloadsPageItem[];
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

export function groupDownloadsForPage({
  downloadQueue,
  completedItems,
  books,
  cycles,
}: {
  downloadQueue: DownloadQueueState;
  completedItems: CompletedDownloadItem[];
  books: Book[];
  cycles: Cycle[];
}): DownloadsPageGroups {
  const byBookId = new Map(books.map((book) => [book.id, book]));
  const cycleByBookId = new Map<string, Cycle>();
  for (const cycle of cycles) {
    for (const book of cycle.books) {
      cycleByBookId.set(book.id, cycle);
    }
  }

  const items = [
    ...activeDownloadItems(downloadQueue).map(queueItemToPageItem),
    ...completedItems.map(completedItemToPageItem),
  ];
  const music: DownloadsPageItem[] = [];
  const audiobookCycles = new Map<string, DownloadsCycleGroup>();
  const standaloneBooks = new Map<string, DownloadsBookGroup>();

  for (const item of items) {
    const book = byBookId.get(item.bookId);
    const contentType = book?.contentType ?? item.contentType;
    if (contentType === "music") {
      music.push(item);
      continue;
    }

    const bookGroup: DownloadsBookGroup = {
      bookId: item.bookId,
      title: book?.title ?? item.subtitle ?? item.bookId,
      items: [],
    };
    const cycle = cycleByBookId.get(item.bookId);
    if (cycle) {
      const cycleGroup = getOrInsert(audiobookCycles, cycle.id, () => ({
        cycleId: cycle.id,
        title: cycle.title,
        books: [],
      }));
      const existing = cycleGroup.books.find((group) => group.bookId === item.bookId);
      if (existing) {
        existing.items.push(item);
      } else {
        bookGroup.items.push(item);
        cycleGroup.books.push(bookGroup);
      }
      continue;
    }

    getOrInsert(standaloneBooks, item.bookId, () => bookGroup).items.push(item);
  }

  return {
    audiobookCycles: Array.from(audiobookCycles.values()),
    audiobookStandaloneBooks: Array.from(standaloneBooks.values()),
    music,
  };
}

function queueItemToPageItem(item: DownloadQueueItem): DownloadsPageItem {
  return {
    bookId: item.bookId,
    trackId: item.trackId,
    title: item.title,
    subtitle: item.subtitle,
    contentType: item.contentType,
    status: item.status,
    progress: item.progress,
    completedAt: item.completedAt,
  };
}

function completedItemToPageItem(item: CompletedDownloadItem): DownloadsPageItem {
  return {
    bookId: item.bookId,
    trackId: item.trackId,
    title: item.title,
    subtitle: item.subtitle,
    contentType: item.contentType,
    status: "COMPLETED",
    progress: 1,
    durationMs: item.durationMs,
    completedAt: item.completedAt,
  };
}

function getOrInsert<K, V>(map: Map<K, V>, key: K, create: () => V): V {
  const existing = map.get(key);
  if (existing) return existing;
  const next = create();
  map.set(key, next);
  return next;
}
