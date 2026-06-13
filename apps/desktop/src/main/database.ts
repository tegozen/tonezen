import Database from "better-sqlite3";
import path from "node:path";
import { booksForCycleOrder } from "../shared/cycleBooks.js";
import type { AudiobookProgress, Book, Cycle, Track } from "../shared/types.js";

interface StoredProgress extends AudiobookProgress {
  pendingSync: boolean;
}

let db: Database.Database | null = null;

export const LocalDatabase = {
  init(userDataPath: string): void {
    const dbPath = path.join(userDataPath, "tonezen.db");
    db = new Database(dbPath);
    db.exec(`
      CREATE TABLE IF NOT EXISTS books (
        id TEXT PRIMARY KEY,
        slug TEXT NOT NULL,
        content_type TEXT NOT NULL,
        title TEXT NOT NULL,
        author TEXT
      );
      CREATE TABLE IF NOT EXISTS tracks (
        id TEXT PRIMARY KEY,
        book_id TEXT NOT NULL,
        sort_order INTEGER NOT NULL,
        title TEXT NOT NULL,
        filename TEXT NOT NULL,
        duration_ms INTEGER,
        local_path TEXT
      );
      CREATE TABLE IF NOT EXISTS audiobook_progress (
        book_id TEXT PRIMARY KEY,
        track_id TEXT NOT NULL,
        position_ms INTEGER NOT NULL,
        updated_at TEXT NOT NULL,
        pending_sync INTEGER NOT NULL DEFAULT 0
      );
      CREATE TABLE IF NOT EXISTS cycles (
        id TEXT PRIMARY KEY,
        slug TEXT NOT NULL,
        title TEXT NOT NULL,
        book_order TEXT NOT NULL,
        books_json TEXT NOT NULL DEFAULT '[]'
      );
    `);
    LocalDatabase.ensureCycleBooksColumn();
  },

  ensureCycleBooksColumn(): void {
    const columns = db!
      .prepare("PRAGMA table_info(cycles)")
      .all() as Array<{ name: string }>;
    if (!columns.some((column) => column.name === "books_json")) {
      db!.exec(`ALTER TABLE cycles ADD COLUMN books_json TEXT NOT NULL DEFAULT '[]'`);
    }
  },

  hydrateCycleBooks(storedBooks: Book[], catalog: Book[]): Book[] {
    const bookById = new Map(catalog.map((book) => [book.id, book]));
    const bookBySlug = new Map(catalog.map((book) => [book.slug, book]));
    return storedBooks
      .map((book) => bookById.get(book.id) ?? bookBySlug.get(book.slug) ?? book)
      .filter((book): book is Book => book != null);
  },

  upsertBooks(books: Book[]): void {
    const stmt = db!.prepare(`
      INSERT INTO books (id, slug, content_type, title, author)
      VALUES (@id, @slug, @contentType, @title, @author)
      ON CONFLICT(id) DO UPDATE SET
        slug = excluded.slug,
        content_type = excluded.content_type,
        title = excluded.title,
        author = excluded.author
    `);
    const tx = db!.transaction((items: Book[]) => {
      for (const book of items) stmt.run(book);
    });
    tx(books);
  },

  upsertTracks(tracks: Track[]): void {
    const stmt = db!.prepare(`
      INSERT INTO tracks (id, book_id, sort_order, title, filename, duration_ms, local_path)
      VALUES (@id, @bookId, @sortOrder, @title, @filename, @durationMs, @localPath)
      ON CONFLICT(id) DO UPDATE SET
        book_id = excluded.book_id,
        sort_order = excluded.sort_order,
        title = excluded.title,
        filename = excluded.filename,
        duration_ms = excluded.duration_ms,
        local_path = COALESCE(excluded.local_path, tracks.local_path)
    `);
    const tx = db!.transaction((items: Track[]) => {
      for (const track of items) {
        stmt.run({
          ...track,
          durationMs: track.durationMs ?? null,
          localPath: track.localPath ?? null,
        });
      }
    });
    tx(tracks);
  },

  setTrackLocalPath(trackId: string, localPath: string | null): void {
    db!.prepare(`UPDATE tracks SET local_path = ? WHERE id = ?`).run(localPath, trackId);
  },

  upsertCycles(cycles: Cycle[]): void {
    const stmt = db!.prepare(`
      INSERT INTO cycles (id, slug, title, book_order, books_json)
      VALUES (@id, @slug, @title, @bookOrder, @booksJson)
      ON CONFLICT(id) DO UPDATE SET
        slug = excluded.slug,
        title = excluded.title,
        book_order = excluded.book_order,
        books_json = excluded.books_json
    `);
    const tx = db!.transaction((items: Cycle[]) => {
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
  },

  getCycles(): Cycle[] {
    const allBooks = this.getBooks();
    const rows = db!
      .prepare(`SELECT id, slug, title, book_order, books_json FROM cycles ORDER BY title`)
      .all() as Array<{
      id: string;
      slug: string;
      title: string;
      book_order: string;
      books_json: string;
    }>;

    return rows.map((r) => {
      const bookOrder = JSON.parse(r.book_order) as string[];
      const storedBooks = JSON.parse(r.books_json || "[]") as Book[];
      const booksFromStored =
        storedBooks.length > 0 ? this.hydrateCycleBooks(storedBooks, allBooks) : [];
      const books =
        booksFromStored.length > 0 ? booksFromStored : booksForCycleOrder(bookOrder, allBooks);
      return {
        id: r.id,
        slug: r.slug,
        title: r.title,
        bookOrder,
        books,
      };
    });
  },

  getAllTracks(): Track[] {
    const rows = db!
      .prepare(
        `SELECT id, book_id, sort_order, title, filename, duration_ms, local_path
         FROM tracks ORDER BY book_id, sort_order`,
      )
      .all() as Array<{
      id: string;
      book_id: string;
      sort_order: number;
      title: string;
      filename: string;
      duration_ms: number | null;
      local_path: string | null;
    }>;
    return rows.map((r) => ({
      id: r.id,
      bookId: r.book_id,
      sortOrder: r.sort_order,
      title: r.title,
      filename: r.filename,
      durationMs: r.duration_ms ?? undefined,
      localPath: r.local_path ?? undefined,
    }));
  },

  getAllProgress(): AudiobookProgress[] {
    const rows = db!
      .prepare(`SELECT book_id, track_id, position_ms, updated_at FROM audiobook_progress`)
      .all() as Array<{
      book_id: string;
      track_id: string;
      position_ms: number;
      updated_at: string;
    }>;
    return rows.map((r) => ({
      bookId: r.book_id,
      trackId: r.track_id,
      positionMs: r.position_ms,
      updatedAt: r.updated_at,
    }));
  },

  getBooks(): Book[] {
    const rows = db!
      .prepare(`SELECT id, slug, content_type, title, author FROM books ORDER BY title`)
      .all() as Array<{
      id: string;
      slug: string;
      content_type: string;
      title: string;
      author: string | null;
    }>;
    return rows.map((r) => ({
      id: r.id,
      slug: r.slug,
      contentType: r.content_type as Book["contentType"],
      title: r.title,
      author: r.author ?? undefined,
    }));
  },

  getTracks(bookId: string): Track[] {
    const rows = db!
      .prepare(
        `SELECT id, book_id, sort_order, title, filename, duration_ms, local_path
         FROM tracks WHERE book_id = ? ORDER BY sort_order`,
      )
      .all(bookId) as Array<{
      id: string;
      book_id: string;
      sort_order: number;
      title: string;
      filename: string;
      duration_ms: number | null;
      local_path: string | null;
    }>;
    return rows.map((r) => ({
      id: r.id,
      bookId: r.book_id,
      sortOrder: r.sort_order,
      title: r.title,
      filename: r.filename,
      durationMs: r.duration_ms ?? undefined,
      localPath: r.local_path ?? undefined,
    }));
  },

  getProgress(bookId: string): StoredProgress | null {
    const row = db!
      .prepare(
        `SELECT book_id, track_id, position_ms, updated_at, pending_sync
         FROM audiobook_progress WHERE book_id = ?`,
      )
      .get(bookId) as
      | {
          book_id: string;
          track_id: string;
          position_ms: number;
          updated_at: string;
          pending_sync: number;
        }
      | undefined;
    if (!row) return null;
    return {
      bookId: row.book_id,
      trackId: row.track_id,
      positionMs: row.position_ms,
      updatedAt: row.updated_at,
      pendingSync: row.pending_sync === 1,
    };
  },

  upsertProgress(progress: AudiobookProgress, pendingSync: boolean): void {
    db!.prepare(
      `INSERT INTO audiobook_progress (book_id, track_id, position_ms, updated_at, pending_sync)
       VALUES (?, ?, ?, ?, ?)
       ON CONFLICT(book_id) DO UPDATE SET
         track_id = excluded.track_id,
         position_ms = excluded.position_ms,
         updated_at = excluded.updated_at,
         pending_sync = excluded.pending_sync`,
    ).run(
      progress.bookId,
      progress.trackId,
      progress.positionMs,
      progress.updatedAt,
      pendingSync ? 1 : 0,
    );
  },

  getPendingProgress(): AudiobookProgress[] {
    const rows = db!
      .prepare(
        `SELECT book_id, track_id, position_ms, updated_at
         FROM audiobook_progress WHERE pending_sync = 1`,
      )
      .all() as Array<{
      book_id: string;
      track_id: string;
      position_ms: number;
      updated_at: string;
    }>;
    return rows.map((r) => ({
      bookId: r.book_id,
      trackId: r.track_id,
      positionMs: r.position_ms,
      updatedAt: r.updated_at,
    }));
  },

  markProgressSynced(bookId: string): void {
    db!.prepare(`UPDATE audiobook_progress SET pending_sync = 0 WHERE book_id = ?`).run(bookId);
  },

  getPendingSyncCount(): number {
    const progress = db!
      .prepare(`SELECT COUNT(*) as count FROM audiobook_progress WHERE pending_sync = 1`)
      .get() as { count: number };
    return progress.count;
  },

  clearAllLocalPaths(): void {
    db!.prepare(`UPDATE tracks SET local_path = NULL`).run();
  },
};
