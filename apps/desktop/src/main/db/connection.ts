import Database from "better-sqlite3";
import path from "node:path";

let db: Database.Database | null = null;

export function getDb(): Database.Database {
  if (!db) throw new Error("Database not initialized");
  return db;
}

export function initDatabase(userDataPath: string): void {
  const dbPath = path.join(userDataPath, "tonezen.db");
  db = new Database(dbPath);
  db.pragma("journal_mode = WAL");
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
      artist TEXT,
      duration_ms INTEGER,
      local_path TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_tracks_book_id ON tracks (book_id);
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
    CREATE TABLE IF NOT EXISTS app_meta (
      key TEXT PRIMARY KEY,
      value TEXT NOT NULL
    );
  `);
  ensureCycleBooksColumn();
  ensureTrackArtistColumn();
  ensureLocalDownloadedAtColumn();
  ensureDownloadQueueTable();
}

function ensureTrackArtistColumn(): void {
  const columns = getDb()
    .prepare("PRAGMA table_info(tracks)")
    .all() as Array<{ name: string }>;
  if (!columns.some((column) => column.name === "artist")) {
    getDb().exec(`ALTER TABLE tracks ADD COLUMN artist TEXT`);
  }
}

function ensureCycleBooksColumn(): void {
  const columns = getDb()
    .prepare("PRAGMA table_info(cycles)")
    .all() as Array<{ name: string }>;
  if (!columns.some((column) => column.name === "books_json")) {
    getDb().exec(`ALTER TABLE cycles ADD COLUMN books_json TEXT NOT NULL DEFAULT '[]'`);
  }
}

function ensureLocalDownloadedAtColumn(): void {
  const columns = getDb()
    .prepare("PRAGMA table_info(tracks)")
    .all() as Array<{ name: string }>;
  if (!columns.some((column) => column.name === "local_downloaded_at")) {
    getDb().exec(`ALTER TABLE tracks ADD COLUMN local_downloaded_at INTEGER`);
  }
}

function ensureDownloadQueueTable(): void {
  getDb().exec(`
    CREATE TABLE IF NOT EXISTS download_queue (
      book_id TEXT NOT NULL,
      track_id TEXT NOT NULL,
      priority TEXT NOT NULL,
      batch_id TEXT,
      enqueued_at INTEGER NOT NULL,
      title TEXT NOT NULL,
      subtitle TEXT,
      content_type TEXT NOT NULL,
      status TEXT NOT NULL,
      bytes_downloaded INTEGER NOT NULL DEFAULT 0,
      total_bytes INTEGER,
      temp_path TEXT,
      PRIMARY KEY (book_id, track_id)
    );
  `);
}
