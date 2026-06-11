import Database from "better-sqlite3";
import path from "node:path";
import type { Book, Track } from "../shared/types.js";

let db: Database.Database | null = null;

export const LocalDatabase = {
  init(userDataPath: string): void {
    const dbPath = path.join(userDataPath, "tplayer.db");
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
      CREATE TABLE IF NOT EXISTS favorites (
        book_id TEXT PRIMARY KEY,
        pending_sync INTEGER NOT NULL DEFAULT 0
      );
    `);
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
};
