import { booksForCycleOrder } from "@core/catalog/cycleBooks.js";
import type { Book, Cycle, Track } from "@core/types.js";
import { getDb } from "../db/connection.js";

export interface LibrarySnapshotOptions {
  reconcileLocalPaths?: boolean;
}

export type CatalogCyclesDeps = {
  hydrateCycleBooks: (storedBooks: Book[], catalog: Book[]) => Book[];
  getBooks: () => Book[];
  getAllTracks: () => Track[];
  reconcileLocalDownloadPaths: (downloadsRoot: string) => void;
};

export function upsertCycles(cycles: Cycle[]): void {
  const stmt = getDb().prepare(`
    INSERT INTO cycles (id, slug, title, book_order, books_json)
    VALUES (@id, @slug, @title, @bookOrder, @booksJson)
    ON CONFLICT(id) DO UPDATE SET
      slug = excluded.slug,
      title = excluded.title,
      book_order = excluded.book_order,
      books_json = excluded.books_json
  `);
  const tx = getDb().transaction((items: Cycle[]) => {
    for (const cycle of items) {
      const bookOrder =
        cycle.books.length > 0 ? cycle.books.map((book) => book.slug) : cycle.bookOrder;
      stmt.run({
        id: cycle.id,
        slug: cycle.slug,
        title: cycle.title,
        bookOrder: JSON.stringify(bookOrder),
        booksJson: JSON.stringify(cycle.books),
      });
    }
  });
  tx(cycles);
}

/** Drop cycles removed from the remote catalog (full sync). */
export function deleteCyclesNotIn(cycleIds: string[]): void {
  if (cycleIds.length === 0) {
    getDb().prepare(`DELETE FROM cycles`).run();
    return;
  }
  const placeholders = cycleIds.map(() => "?").join(",");
  getDb()
    .prepare(`DELETE FROM cycles WHERE id NOT IN (${placeholders})`)
    .run(...cycleIds);
}

export function createCatalogCyclesDb(deps: CatalogCyclesDeps) {
  const methods = {
    getCycles(allBooks?: Book[]): Cycle[] {
      return methods.buildCycles(allBooks ?? deps.getBooks());
    },

    getLibrarySnapshot(
      downloadsRoot: string,
      options: LibrarySnapshotOptions = {},
    ): { books: Book[]; cycles: Cycle[]; tracks: Track[] } {
      if (options.reconcileLocalPaths !== false) {
        deps.reconcileLocalDownloadPaths(downloadsRoot);
      }
      const books = deps.getBooks();
      return {
        books,
        cycles: methods.buildCycles(books),
        tracks: deps.getAllTracks(),
      };
    },

    buildCycles(allBooks: Book[]): Cycle[] {
      const rows = getDb()
        .prepare(`SELECT id, slug, title, book_order, books_json FROM cycles ORDER BY title`)
        .all() as Array<{
        id: string;
        slug: string;
        title: string;
        book_order: string;
        books_json: string;
      }>;

      return rows.map((row) => {
        const bookOrder = JSON.parse(row.book_order) as string[];
        const storedBooks = JSON.parse(row.books_json || "[]") as Book[];
        const booksFromStored =
          storedBooks.length > 0 ? deps.hydrateCycleBooks(storedBooks, allBooks) : [];
        const books =
          booksFromStored.length > 0 ? booksFromStored : booksForCycleOrder(bookOrder, allBooks);
        return {
          id: row.id,
          slug: row.slug,
          title: row.title,
          bookOrder,
          books,
        };
      });
    },
  };

  return {
    upsertCycles,
    ...methods,
  };
}
