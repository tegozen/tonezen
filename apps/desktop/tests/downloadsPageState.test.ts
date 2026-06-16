import { describe, expect, it } from "vitest";
import { completedDownloadItems } from "../src/shared/downloadsPageState.js";
import type { Book, Track } from "../src/shared/types.js";
import type { DownloadQueueState } from "../src/shared/downloadQueueState.js";

const musicBook: Book = {
  id: "music",
  slug: "music-library",
  contentType: "music",
  title: "Music",
};

const track = (
  id: string,
  localDownloadedAt: number,
  title = id,
): Track => ({
  id,
  bookId: musicBook.id,
  sortOrder: 0,
  title,
  filename: `${id}.mp3`,
  durationMs: 1000,
  localPath: `/downloads/${id}.mp3`,
  localDownloadedAt,
});

const queue = {
  queuedItems: [],
  completedHistory: [
    {
      bookId: musicBook.id,
      trackId: "live",
      title: "Live",
      subtitle: "Artist",
      contentType: "music",
      status: "COMPLETED",
      progress: 1,
      batchId: null,
      enqueuedAt: 50,
      completedAt: 300,
    },
  ],
  activeBookId: null,
  activeTrackId: null,
  trackProgress: null,
  bulkDownloaded: 0,
  bulkTotal: 0,
  activeBatchId: null,
  pausedForNetwork: false,
} satisfies DownloadQueueState;

describe("completedDownloadItems", () => {
  it("merges persisted local downloads with live queue history", () => {
    const result = completedDownloadItems(
      queue,
      [track("persisted", 100, "Persisted")],
      [musicBook],
    );

    expect(result.map((item) => item.trackId)).toEqual(["persisted", "live"]);
    expect(result[0]).toMatchObject({
      bookId: musicBook.id,
      trackId: "persisted",
      title: "Persisted",
      contentType: "music",
      durationMs: 1000,
      completedAt: 100,
    });
  });

  it("prefers live queue metadata for duplicate completed tracks", () => {
    const result = completedDownloadItems(
      queue,
      [track("live", 100, "Stale title")],
      [musicBook],
    );

    expect(result).toHaveLength(1);
    expect(result[0]).toMatchObject({
      trackId: "live",
      title: "Live",
      subtitle: "Artist",
      completedAt: 300,
    });
  });
});
