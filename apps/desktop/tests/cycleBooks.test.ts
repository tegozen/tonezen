import { describe, expect, it } from "vitest";
import { booksForCycleOrder, normalizeCycleBookOrder } from "../src/shared/cycleBooks.js";

const catalog = [
  {
    id: "book-uuid-1",
    slug: "defender-book-one",
    contentType: "audiobook" as const,
    title: "Book One",
  },
  {
    id: "book-uuid-2",
    slug: "defender-book-two",
    contentType: "audiobook" as const,
    title: "Book Two",
  },
];

describe("booksForCycleOrder", () => {
  it("resolves books by slug", () => {
    const books = booksForCycleOrder(["defender-book-one", "defender-book-two"], catalog);

    expect(books.map((book) => book.slug)).toEqual(["defender-book-one", "defender-book-two"]);
  });

  it("resolves books by id", () => {
    const books = booksForCycleOrder(["book-uuid-1", "book-uuid-2"], catalog);

    expect(books.map((book) => book.id)).toEqual(["book-uuid-1", "book-uuid-2"]);
  });
});

describe("normalizeCycleBookOrder", () => {
  it("falls back to book slugs when API order is empty", () => {
    expect(normalizeCycleBookOrder([], catalog)).toEqual(["defender-book-one", "defender-book-two"]);
  });
});
