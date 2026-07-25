import fs from "node:fs";
import path from "node:path";
import { booksForCycleOrder } from "@core/catalog/cycleBooks.js";
import {
  isSafeStorageId,
  resolveTrackDownloadPath,
  sanitizeLocalAudioPath,
} from "@core/platform/safeLocalPaths.js";
import type { Book, Cycle, Track } from "@core/types.js";
import { serializeWaveformPeaks } from "@core/catalog/waveformPeaks.js";
import { getDb } from "../db/connection.js";
import { mapBookRow, mapTrackRow, type BookRow, type TrackRow } from "../db/mappers.js";

export interface LibrarySnapshotOptions {
  reconcileLocalPaths?: boolean;
}

export const CatalogDb = {
  hydrateCycleBooks(storedBooks: Book[], catalog: Book[]): Book[] {
    const bookById = new Map(catalog.map((book) => [book.id, book]));
    const bookBySlug = new Map(catalog.map((book) => [book.slug, book]));
    return storedBooks
      .map((book) => bookById.get(book.id) ?? bookBySlug.get(book.slug) ?? book)
      .filter((book): book is Book => book != null);
  },

  upsertBooks(books: Book[]): void {
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
  },

  upsertTracks(tracks: Track[]): void {
    const stmt = getDb().prepare(`
      INSERT INTO tracks (id, book_id, sort_order, title, filename, artist, duration_ms, local_path, waveform_peaks_json)
      VALUES (@id, @bookId, @sortOrder, @title, @filename, @artist, @durationMs, @localPath, @waveformPeaksJson)
      ON CONFLICT(id) DO UPDATE SET
        book_id = excluded.book_id,
        sort_order = excluded.sort_order,
        title = excluded.title,
        filename = excluded.filename,
        artist = excluded.artist,
        duration_ms = excluded.duration_ms,
        local_path = COALESCE(excluded.local_path, tracks.local_path),
        waveform_peaks_json = excluded.waveform_peaks_json
    `);
    const tx = getDb().transaction((items: Track[]) => {
      for (const track of items) {
        stmt.run({
          ...track,
          artist: track.artist ?? null,
          durationMs: track.durationMs ?? null,
          localPath: track.localPath ?? null,
          waveformPeaksJson: serializeWaveformPeaks(track.waveformPeaks),
        });
      }
    });
    tx(tracks);
  },

  setTrackLocalPath(trackId: string, localPath: string | null, downloadsRoot?: string): void {
    if (localPath == null) {
      getDb()
        .prepare(`UPDATE tracks SET local_path = NULL, local_downloaded_at = NULL WHERE id = ?`)
        .run(trackId);
      return;
    }
    const safePath =
      downloadsRoot != null
        ? sanitizeLocalAudioPath(localPath, [downloadsRoot])
        : null;
    if (!safePath) {
      getDb()
        .prepare(`UPDATE tracks SET local_path = NULL, local_downloaded_at = NULL WHERE id = ?`)
        .run(trackId);
      return;
    }
    getDb()
      .prepare(`UPDATE tracks SET local_path = ?, local_downloaded_at = ? WHERE id = ?`)
      .run(safePath, Date.now(), trackId);
  },

  getTrackById(trackId: string): Track | null {
    const row = getDb()
      .prepare(
        `SELECT id, book_id, sort_order, title, filename, artist, duration_ms, local_path, local_downloaded_at, waveform_peaks_json
         FROM tracks WHERE id = ? LIMIT 1`,
      )
      .get(trackId) as TrackRow | undefined;
    return row ? mapTrackRow(row) : null;
  },

  markTrackDownloaded(
    bookId: string,
    trackId: string,
    localPath: string,
    downloadsRoot: string,
  ): boolean {
    const safePath = sanitizeLocalAudioPath(localPath, [downloadsRoot]);
    if (!safePath || !fs.existsSync(safePath) || fs.statSync(safePath).size <= 0) return false;
    const track =
      this.getTracks(bookId).find((item) => item.id === trackId) ?? this.getTrackById(trackId);
    if (!track) return false;
    getDb()
      .prepare(`UPDATE tracks SET local_path = ?, local_downloaded_at = ? WHERE id = ?`)
      .run(safePath, Date.now(), trackId);
    return true;
  },

  resolveLocalTrackPath(bookId: string, trackId: string, downloadsRoot: string): string | null {
    const direct = this.resolveLocalTrackPathForBook(bookId, trackId, downloadsRoot);
    if (direct) return direct;
    const onDisk = findOnDiskTrackPath(trackId, downloadsRoot);
    if (!onDisk) return null;
    const [diskBookId, path] = onDisk;
    this.markTrackDownloaded(diskBookId, trackId, path, downloadsRoot);
    return path;
  },

  resolveLocalTrackPathForBook(bookId: string, trackId: string, downloadsRoot: string): string | null {
    const track = this.getTracks(bookId).find((item) => item.id === trackId);
    if (track?.localPath) {
      const safePath = sanitizeLocalAudioPath(track.localPath, [downloadsRoot]);
      if (safePath && fs.existsSync(safePath) && fs.statSync(safePath).size > 0) {
        return safePath;
      }
    }
    const onDisk = resolveTrackDownloadPath(downloadsRoot, bookId, trackId);
    if (onDisk && fs.existsSync(onDisk) && fs.statSync(onDisk).size > 0) {
      this.markTrackDownloaded(bookId, trackId, onDisk, downloadsRoot);
      return onDisk;
    }
    if (track?.localPath) {
      this.setTrackLocalPath(trackId, null);
    }
    return null;
  },

  upsertCycles(cycles: Cycle[]): void {
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
  },

  getCycles(allBooks?: Book[]): Cycle[] {
    return this.buildCycles(allBooks ?? this.getBooks());
  },

  getLibrarySnapshot(
    downloadsRoot: string,
    options: LibrarySnapshotOptions = {},
  ): { books: Book[]; cycles: Cycle[]; tracks: Track[] } {
    if (options.reconcileLocalPaths !== false) {
      this.reconcileLocalDownloadPaths(downloadsRoot);
    }
    const books = this.getBooks();
    return {
      books,
      cycles: this.buildCycles(books),
      tracks: this.getAllTracks(),
    };
  },

  reconcileLocalDownloadPaths(downloadsRoot: string): void {
    const onDiskByKey = scanDownloadedFilesOnDisk(downloadsRoot);
    const rows = getDb()
      .prepare(
        `SELECT id, book_id, sort_order, title, filename, artist, duration_ms, local_path, local_downloaded_at, waveform_peaks_json
         FROM tracks`,
      )
      .all() as TrackRow[];

    for (const row of rows) {
      const track = mapTrackRow(row);
      const diskPath = onDiskByKey.get(trackKey(track.bookId, track.id));
      const safeStored = track.localPath
        ? sanitizeLocalAudioPath(track.localPath, [downloadsRoot])
        : null;
      const storedValid =
        safeStored != null && fs.existsSync(safeStored) && fs.statSync(safeStored).size > 0;

      if (!storedValid && diskPath) {
        this.markTrackDownloaded(track.bookId, track.id, diskPath, downloadsRoot);
        continue;
      }
      if (storedValid) continue;
      if (track.localPath) {
        this.setTrackLocalPath(track.id, null);
      }
    }
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
        storedBooks.length > 0 ? this.hydrateCycleBooks(storedBooks, allBooks) : [];
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

  getAllTracks(): Track[] {
    const rows = getDb()
      .prepare(
        `SELECT id, book_id, sort_order, title, filename, artist, duration_ms, local_path, local_downloaded_at, waveform_peaks_json
         FROM tracks ORDER BY book_id, sort_order`,
      )
      .all() as TrackRow[];
    return rows.map(mapTrackRow);
  },

  getBooks(): Book[] {
    const rows = getDb()
      .prepare(`SELECT id, slug, content_type, title, author FROM books ORDER BY title`)
      .all() as BookRow[];
    return rows.map(mapBookRow);
  },

  getTracks(bookId: string): Track[] {
    const rows = getDb()
      .prepare(
        `SELECT id, book_id, sort_order, title, filename, artist, duration_ms, local_path, local_downloaded_at, waveform_peaks_json
         FROM tracks WHERE book_id = ? ORDER BY sort_order`,
      )
      .all(bookId) as TrackRow[];
    return rows.map(mapTrackRow);
  },

  clearAllLocalPaths(): void {
    getDb().prepare(`UPDATE tracks SET local_path = NULL, local_downloaded_at = NULL`).run();
  },
};

function trackKey(bookId: string, trackId: string): string {
  return `${bookId}\0${trackId}`;
}

function findOnDiskTrackPath(trackId: string, downloadsRoot: string): [string, string] | null {
  for (const [key, filePath] of scanDownloadedFilesOnDisk(downloadsRoot)) {
    const separator = key.indexOf("\0");
    if (separator < 0) continue;
    const bookId = key.slice(0, separator);
    const id = key.slice(separator + 1);
    if (id === trackId) return [bookId, filePath];
  }
  return null;
}

function scanDownloadedFilesOnDisk(downloadsRoot: string): Map<string, string> {
  const result = new Map<string, string>();
  if (!fs.existsSync(downloadsRoot)) return result;
  for (const bookEntry of fs.readdirSync(downloadsRoot, { withFileTypes: true })) {
    if (!bookEntry.isDirectory()) continue;
    const bookId = bookEntry.name;
    if (!isSafeStorageId(bookId)) continue;
    const bookDir = path.join(downloadsRoot, bookId);
    for (const fileEntry of fs.readdirSync(bookDir, { withFileTypes: true })) {
      if (!fileEntry.isFile()) continue;
      if (!fileEntry.name.endsWith(".mp3")) continue;
      const trackId = fileEntry.name.slice(0, -4);
      if (!isSafeStorageId(trackId)) continue;
      const fullPath = path.join(bookDir, fileEntry.name);
      const safePath = sanitizeLocalAudioPath(fullPath, [downloadsRoot]);
      if (safePath && fs.existsSync(safePath) && fs.statSync(safePath).size > 0) {
        result.set(trackKey(bookId, trackId), safePath);
      }
    }
  }
  return result;
}
