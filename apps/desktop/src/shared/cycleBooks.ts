import type { Book } from "./types.js";

export function booksForCycleOrder(bookOrder: string[], catalog: Book[]): Book[] {
  const bookById = new Map(catalog.map((book) => [book.id, book]));
  const bookBySlug = new Map(catalog.map((book) => [book.slug, book]));

  return bookOrder
    .map((key) => bookById.get(key) ?? bookBySlug.get(key))
    .filter((book): book is Book => book != null);
}

export function normalizeCycleBookOrder(bookOrder: string[], books: Book[]): string[] {
  if (bookOrder.length > 0) return bookOrder;
  return books.map((book) => book.slug);
}
