import { describe, expect, it } from "vitest";
import {
  completedAudiobookProgress,
  upsertAudiobookProgress,
} from "../src/shared/audiobookProgress.js";
import type { AudiobookProgress, Book, Track } from "../src/shared/types.js";

const book: Book = {
  id: "book-1",
  slug: "book",
  title: "Book",
  contentType: "audiobook",
};

const track: Track = {
  id: "track-10",
  bookId: "book-1",
  sortOrder: 9,
  title: "010",
  filename: "010.mp3",
  durationMs: 1_164_000,
};

describe("completedAudiobookProgress", () => {
  it("builds completed progress for the chapter that just ended", () => {
    expect(completedAudiobookProgress(book, track, 0, "2026-06-23T09:00:00.000Z")).toEqual({
      bookId: "book-1",
      trackId: "track-10",
      positionMs: 1_164_000,
      updatedAt: "2026-06-23T09:00:00.000Z",
    });
  });

  it("ignores non-audiobook progress", () => {
    expect(
      completedAudiobookProgress({ ...book, contentType: "music" }, track, 1_164_000),
    ).toBeNull();
  });
});

describe("upsertAudiobookProgress", () => {
  it("replaces stale progress for the same book", () => {
    const stale: AudiobookProgress = {
      bookId: "book-1",
      trackId: "track-10",
      positionMs: 651_840,
      updatedAt: "2026-06-23T08:00:00.000Z",
    };
    const completed = completedAudiobookProgress(
      book,
      track,
      0,
      "2026-06-23T09:00:00.000Z",
    );

    expect(completed).not.toBeNull();
    expect(upsertAudiobookProgress([stale], completed!)).toEqual([completed]);
  });
});
