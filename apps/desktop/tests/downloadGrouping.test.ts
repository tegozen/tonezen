import { describe, expect, it } from "vitest";
import { groupDownloadsForPage, type CompletedDownloadItem } from "../src/shared/downloadsPageState.js";
import type { Book, Cycle } from "../src/shared/types.js";
import type { DownloadQueueState } from "../src/shared/downloadQueueState.js";

const books: Book[] = [
  { id: "book-1", slug: "book-1", title: "Первая книга", contentType: "audiobook" },
  { id: "book-2", slug: "book-2", title: "Вторая книга", contentType: "audiobook" },
  { id: "music", slug: "music", title: "Музыка", contentType: "music" },
];

const cycles: Cycle[] = [
  {
    id: "cycle-1",
    slug: "cycle-1",
    title: "Большой цикл",
    bookOrder: ["book-1", "book-2"],
    books: [books[0], books[1]],
  },
];

const queue: DownloadQueueState = {
  queuedItems: [
    {
      bookId: "book-1",
      trackId: "chapter-2",
      title: "002",
      subtitle: "Первая книга",
      contentType: "audiobook",
      status: "DOWNLOADING",
      progress: 0.4,
      batchId: null,
      enqueuedAt: 20,
      completedAt: null,
    },
    {
      bookId: "music",
      trackId: "song-1",
      title: "Песня",
      subtitle: "Исполнитель",
      contentType: "music",
      status: "QUEUED",
      progress: null,
      batchId: null,
      enqueuedAt: 10,
      completedAt: null,
    },
  ],
  completedHistory: [],
  activeBookId: "book-1",
  activeTrackId: "chapter-2",
  trackProgress: 0.4,
  bulkDownloaded: 0,
  bulkTotal: 0,
  activeBatchId: null,
  pausedForNetwork: false,
};

const completedItems: CompletedDownloadItem[] = [
  {
    bookId: "book-1",
    trackId: "chapter-1",
    title: "001",
    subtitle: "Первая книга",
    contentType: "audiobook",
    durationMs: 60_000,
    completedAt: 100,
  },
  {
    bookId: "music",
    trackId: "song-2",
    title: "Готовая песня",
    subtitle: "Исполнитель",
    contentType: "music",
    durationMs: 120_000,
    completedAt: 200,
  },
];

describe("groupDownloadsForPage", () => {
  it("separates music from audiobooks and groups audiobook tracks by cycle and book", () => {
    const result = groupDownloadsForPage({
      downloadQueue: queue,
      completedItems,
      books,
      cycles,
    });

    expect(result.music.map((item) => item.trackId)).toEqual(["song-1", "song-2"]);
    expect(result.audiobookCycles).toHaveLength(1);
    expect(result.audiobookCycles[0]).toMatchObject({
      cycleId: "cycle-1",
      title: "Большой цикл",
    });
    expect(result.audiobookCycles[0].books).toHaveLength(1);
    expect(result.audiobookCycles[0].books[0]).toMatchObject({
      bookId: "book-1",
      title: "Первая книга",
    });
    expect(result.audiobookCycles[0].books[0].items.map((item) => item.trackId)).toEqual([
      "chapter-2",
      "chapter-1",
    ]);
    expect(result.audiobookStandaloneBooks).toEqual([]);
  });
});
