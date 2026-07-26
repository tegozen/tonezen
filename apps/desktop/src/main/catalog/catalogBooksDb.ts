import type { Book } from "@core/types.js";
import { getDb } from "../db/connection.js";
import { mapBookRow, type BookRow } from "../db/mappers.js";

export function hydrateCycleBooks(storedBooks: Book[], catalog: Book[]): Book[] {
  const bookById = new Map(catalog.map((book) => [book.id, book]));
  const bookBySlug = new Map(catalog.map((book) => [book.slug, book]));
  return storedBooks
    .map((book) => bookById.get(book.id) ?? bookBySlug.get(book.slug) ?? book)
    .filter((book): book is Book => book != null);
}

export function upsertBooks(books: Book[]): void {
  const stmt = getDb().prepare(`
    INSERT INTO books (id, slug, content_type, title, author)
    VALUES (@id, @slug, @contentType, @title, @author)
    ON CONFLICT(id) DO UPDATE SET
      slug = excluded.slug,
      content_type = excluded.content_type,
      title = excluded.title,
      author = excluded.author
  `);
  const tx = getDb().transaction((items: Book[]) => {
    for (const book of items) stmt.run(book);
  });
  tx(books);
}

/** Drop books removed from the remote catalog (full sync). */
export function deleteBooksNotIn(bookIds: string[]): void {
  if (bookIds.length === 0) {
    getDb().prepare(`DELETE FROM books`).run();
    return;
  }
  const placeholders = bookIds.map(() => "?").join(",");
  getDb()
    .prepare(`DELETE FROM books WHERE id NOT IN (${placeholders})`)
    .run(...bookIds);
}

export function getBooks(): Book[] {
  const rows = getDb()
    .prepare(`SELECT id, slug, content_type, title, author FROM books ORDER BY title`)
    .all() as BookRow[];
  return rows.map(mapBookRow);
}
