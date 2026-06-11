import { describe, expect, it } from "vitest";
import { CyclePlaybackResolver } from "../src/shared/cyclePlayback.js";
import type { Book, Cycle, Track } from "../src/shared/types.js";

describe("CyclePlaybackResolver", () => {
  const resolver = new CyclePlaybackResolver();
  const book1: Book = {
    id: "b1",
    slug: "book-one",
    contentType: "audiobook",
    title: "Book One",
  };
  const book2: Book = {
    id: "b2",
    slug: "book-two",
    contentType: "audiobook",
    title: "Book Two",
  };
  const cycle: Cycle = {
    id: "c1",
    slug: "cycle",
    title: "Cycle",
    bookOrder: ["book-one", "book-two"],
    books: [book1, book2],
  };
  const t1: Track = {
    id: "t1",
    bookId: "b1",
    sortOrder: 0,
    title: "Intro",
    filename: "001.mp3",
  };
  const t2: Track = {
    id: "t2",
    bookId: "b1",
    sortOrder: 1,
    title: "Chapter",
    filename: "002.mp3",
  };
  const t3: Track = {
    id: "t3",
    bookId: "b2",
    sortOrder: 0,
    title: "Start",
    filename: "001.mp3",
  };

  it("advances to next book in cycle", () => {
    const result = resolver.nextInCycle(
      book1,
      t2,
      cycle,
      new Map([
        ["book-one", book1],
        ["book-two", book2],
      ]),
      new Map([
        ["b1", [t1, t2]],
        ["b2", [t3]],
      ]),
    );
    expect(result.isNextBookInCycle).toBe(true);
    expect(result.track?.id).toBe("t3");
  });
});
