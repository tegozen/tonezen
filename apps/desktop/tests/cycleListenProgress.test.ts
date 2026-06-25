import { describe, expect, it } from "vitest";
import {
  orderedCycleEntriesFromResume,
  resolveBookListenedMs,
  resolveCycleResumeTarget,
} from "../src/shared/cycleListenProgress.js";
import type { AudiobookProgress, Book, Cycle, Track } from "../src/shared/types.js";

const tracks: Track[] = [
  { id: "t1", bookId: "b1", sortOrder: 0, title: "One", filename: "1.mp3", durationMs: 100_000 },
  { id: "t2", bookId: "b1", sortOrder: 1, title: "Two", filename: "2.mp3", durationMs: 100_000 },
];

describe("resolveBookListenedMs", () => {
  it("sums completed chapters and current position", () => {
    const progress: AudiobookProgress = {
      bookId: "b1",
      trackId: "t2",
      positionMs: 40_000,
      updatedAt: "2026-01-01T00:00:00.000Z",
    };
    expect(resolveBookListenedMs(tracks, progress)).toBe(140_000);
  });

  it("returns zero when track does not belong to the book", () => {
    const progress: AudiobookProgress = {
      bookId: "b1",
      trackId: "t1",
      positionMs: 10_000,
      updatedAt: "2026-01-01T00:00:00.000Z",
    };
    expect(resolveBookListenedMs([{ ...tracks[0], bookId: "other" }], progress)).toBe(0);
  });
});

const bookOne: Book = {
  id: "book-1",
  slug: "book-one",
  title: "Book one",
  contentType: "audiobook",
};
const bookTwo: Book = {
  id: "book-2",
  slug: "book-two",
  title: "Book two",
  contentType: "audiobook",
};
const cycle: Cycle = {
  id: "cycle-1",
  slug: "cycle",
  title: "Cycle",
  bookOrder: [bookOne.slug, bookTwo.slug],
  books: [bookOne, bookTwo],
};
const cycleTracks = new Map<string, Track[]>([
  [bookOne.id, [{ id: "t1", bookId: bookOne.id, sortOrder: 0, title: "t1", filename: "1.mp3", durationMs: 100_000 }]],
  [
    bookTwo.id,
    [
      { id: "t2", bookId: bookTwo.id, sortOrder: 0, title: "t2", filename: "2.mp3", durationMs: 100_000 },
      { id: "t3", bookId: bookTwo.id, sortOrder: 1, title: "t3", filename: "3.mp3", durationMs: 100_000 },
    ],
  ],
]);

describe("resolveCycleResumeTarget", () => {
  it("returns first track when nothing listened", () => {
    const resume = resolveCycleResumeTarget(cycle, cycleTracks, new Map());
    expect(resume?.track.id).toBe("t1");
    expect(resume?.startPositionMs).toBe(0);
  });

  it("continues partial chapter", () => {
    const progress = new Map<string, AudiobookProgress>([
      [bookOne.id, { bookId: bookOne.id, trackId: "t1", positionMs: 100_000, updatedAt: "2026-01-01T00:00:00.000Z" }],
      [bookTwo.id, { bookId: bookTwo.id, trackId: "t2", positionMs: 40_000, updatedAt: "2026-01-01T00:00:00.000Z" }],
    ]);
    const resume = resolveCycleResumeTarget(cycle, cycleTracks, progress);
    expect(resume?.track.id).toBe("t2");
    expect(resume?.startPositionMs).toBe(40_000);
  });
});

describe("orderedCycleEntriesFromResume", () => {
  it("orders entries from resume target forward", () => {
    const resume = {
      book: bookTwo,
      track: cycleTracks.get(bookTwo.id)![1],
      startPositionMs: 0,
    };
    const entries = orderedCycleEntriesFromResume(cycle, cycleTracks, resume);
    expect(entries.map((entry) => entry.track.id)).toEqual(["t3"]);
  });
});
