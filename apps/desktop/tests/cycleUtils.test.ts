import { describe, expect, it } from "vitest";
import {
  buildTracksByBookId,
  isBookFullyDownloaded,
  resolveCycleContinueState,
} from "../src/renderer/src/lib/cycleUtils.js";
import type { AudiobookProgress, Book, Cycle, Track } from "../src/shared/types.js";

const tracks: Track[] = [
  {
    id: "t1",
    bookId: "b1",
    sortOrder: 1,
    title: "One",
    filename: "one.mp3",
    localPath: "/downloads/b1/t1.mp3",
  },
  {
    id: "t2",
    bookId: "b1",
    sortOrder: 2,
    title: "Two",
    filename: "two.mp3",
  },
  {
    id: "t3",
    bookId: "b2",
    sortOrder: 1,
    title: "Three",
    filename: "three.mp3",
    localPath: "/downloads/b2/t3.mp3",
  },
];

describe("buildTracksByBookId", () => {
  it("groups tracks by book id in one pass", () => {
    const map = buildTracksByBookId(tracks);
    expect(map.get("b1")?.map((t) => t.id)).toEqual(["t1", "t2"]);
    expect(map.get("b2")?.map((t) => t.id)).toEqual(["t3"]);
  });
});

describe("isBookFullyDownloaded", () => {
  it("uses grouped tracks without scanning the full library", () => {
    const map = buildTracksByBookId(tracks);
    expect(isBookFullyDownloaded("b1", map)).toBe(false);
    expect(isBookFullyDownloaded("b2", map)).toBe(true);
  });
});

describe("resolveCycleContinueState", () => {
  const books: Book[] = [
    { id: "b1", slug: "book-1", contentType: "audiobook", title: "Book 1" },
    { id: "b2", slug: "book-2", contentType: "audiobook", title: "Book 2" },
  ];
  const cycle: Cycle = {
    id: "c1",
    slug: "cycle-1",
    title: "Cycle",
    bookOrder: ["book-1", "book-2"],
    books,
  };
  const cycleTracks: Track[] = [
    { id: "t1", bookId: "b1", sortOrder: 0, title: "Chapter 1", filename: "1.mp3", durationMs: 100_000 },
    { id: "t2", bookId: "b2", sortOrder: 0, title: "Chapter A", filename: "a.mp3", durationMs: 100_000 },
  ];
  const tracksByBookId = buildTracksByBookId(cycleTracks);

  it("returns the latest resumable chapter in cycle order", () => {
    const progressByBook = new Map<string, AudiobookProgress>([
      ["b1", { bookId: "b1", trackId: "t1", positionMs: 95_000, updatedAt: "2026-01-01T00:00:00.000Z" }],
      ["b2", { bookId: "b2", trackId: "t2", positionMs: 12_000, updatedAt: "2026-01-02T00:00:00.000Z" }],
    ]);

    expect(resolveCycleContinueState(cycle, tracksByBookId, progressByBook)).toEqual({
      trackTitle: "Chapter A",
      positionMs: 12_000,
    });
  });

  it("returns null when nothing is partially listened", () => {
    expect(resolveCycleContinueState(cycle, tracksByBookId, new Map())).toBeNull();
  });

  it("returns null for another cycle without its own progress", () => {
    const otherCycle: Cycle = {
      id: "c2",
      slug: "cycle-2",
      title: "Other",
      bookOrder: ["book-3"],
      books: [{ id: "b3", slug: "book-3", contentType: "audiobook", title: "Book 3" }],
    };
    const otherTracks = buildTracksByBookId([
      { id: "t3", bookId: "b3", sortOrder: 0, title: "Chapter 1", filename: "1.mp3", durationMs: 100_000 },
    ]);
    const progressByBook = new Map<string, AudiobookProgress>([
      ["b2", { bookId: "b2", trackId: "t2", positionMs: 12_000, updatedAt: "2026-01-02T00:00:00.000Z" }],
    ]);

    expect(resolveCycleContinueState(otherCycle, otherTracks, progressByBook)).toBeNull();
  });
});
