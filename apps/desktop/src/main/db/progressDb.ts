import type { AudiobookProgress } from "../../shared/types.js";
import { getDb } from "./connection.js";

export interface StoredProgress extends AudiobookProgress {
  pendingSync: boolean;
}

export const ProgressDb = {
  getAllProgress(): AudiobookProgress[] {
    const rows = getDb()
      .prepare(`SELECT book_id, track_id, position_ms, updated_at FROM audiobook_progress`)
      .all() as Array<{
      book_id: string;
      track_id: string;
      position_ms: number;
      updated_at: string;
    }>;
    return rows.map((row) => ({
      bookId: row.book_id,
      trackId: row.track_id,
      positionMs: row.position_ms,
      updatedAt: row.updated_at,
    }));
  },

  getProgress(bookId: string): StoredProgress | null {
    const row = getDb()
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
    getDb()
      .prepare(
        `INSERT INTO audiobook_progress (book_id, track_id, position_ms, updated_at, pending_sync)
       VALUES (?, ?, ?, ?, ?)
       ON CONFLICT(book_id) DO UPDATE SET
         track_id = excluded.track_id,
         position_ms = excluded.position_ms,
         updated_at = excluded.updated_at,
         pending_sync = excluded.pending_sync`,
      )
      .run(
        progress.bookId,
        progress.trackId,
        progress.positionMs,
        progress.updatedAt,
        pendingSync ? 1 : 0,
      );
  },

  getPendingProgress(): AudiobookProgress[] {
    const rows = getDb()
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
    return rows.map((row) => ({
      bookId: row.book_id,
      trackId: row.track_id,
      positionMs: row.position_ms,
      updatedAt: row.updated_at,
    }));
  },

  markProgressSynced(bookId: string): void {
    getDb().prepare(`UPDATE audiobook_progress SET pending_sync = 0 WHERE book_id = ?`).run(bookId);
  },

  getPendingSyncCount(): number {
    const progress = getDb()
      .prepare(`SELECT COUNT(*) as count FROM audiobook_progress WHERE pending_sync = 1`)
      .get() as { count: number };
    return progress.count;
  },

  getLastSyncAtEpochMs(): number | null {
    const row = getDb()
      .prepare(`SELECT value FROM app_meta WHERE key = 'last_sync_at_epoch_ms'`)
      .get() as { value: string } | undefined;
    if (!row?.value) return null;
    const parsed = Number(row.value);
    return Number.isFinite(parsed) ? parsed : null;
  },

  setLastSyncAtEpochMs(epochMs: number): void {
    getDb()
      .prepare(
        `INSERT INTO app_meta (key, value) VALUES ('last_sync_at_epoch_ms', @value)
         ON CONFLICT(key) DO UPDATE SET value = excluded.value`,
      )
      .run({ value: String(epochMs) });
  },
};
