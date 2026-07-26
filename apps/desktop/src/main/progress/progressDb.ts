import type { AudiobookProgress } from "@core/types.js";
import { getDb } from "../db/connection.js";

export interface StoredProgress extends AudiobookProgress {
  pendingSync: boolean;
  userId: string;
}

type ProgressRow = {
  user_id: string;
  book_id: string;
  track_id: string;
  position_ms: number;
  updated_at: string;
  pending_sync: number;
  revision: number;
  server_track_id: string | null;
  server_position_ms: number | null;
  server_revision: number | null;
  conflict_choice_key: string | null;
};

let activeUserId: string | null = null;

function mapRow(row: ProgressRow): StoredProgress {
  return {
    userId: row.user_id,
    bookId: row.book_id,
    trackId: row.track_id,
    positionMs: row.position_ms,
    updatedAt: row.updated_at,
    pendingSync: row.pending_sync === 1,
    revision: row.revision,
    serverTrackId: row.server_track_id,
    serverPositionMs: row.server_position_ms,
    serverRevision: row.server_revision,
    conflictChoiceKey: row.conflict_choice_key,
  };
}

function requireUserId(): string {
  if (!activeUserId) throw new Error("Progress DB active user is not set");
  return activeUserId;
}

export const ProgressDb = {
  setActiveUserId(userId: string | null): void {
    activeUserId = userId;
  },

  getActiveUserId(): string | null {
    return activeUserId;
  },

  getAllProgress(): AudiobookProgress[] {
    if (!activeUserId) return [];
    const rows = getDb()
      .prepare(
        `SELECT user_id, book_id, track_id, position_ms, updated_at, pending_sync, revision,
                server_track_id, server_position_ms, server_revision, conflict_choice_key
         FROM audiobook_progress WHERE user_id = ?`,
      )
      .all(activeUserId) as ProgressRow[];
    return rows.map(mapRow);
  },

  countProgressForActiveUser(): number {
    if (!activeUserId) return 0;
    const row = getDb()
      .prepare(`SELECT COUNT(*) as count FROM audiobook_progress WHERE user_id = ?`)
      .get(activeUserId) as { count: number };
    return row.count;
  },

  getProgress(bookId: string): StoredProgress | null {
    if (!activeUserId) return null;
    const row = getDb()
      .prepare(
        `SELECT user_id, book_id, track_id, position_ms, updated_at, pending_sync, revision,
                server_track_id, server_position_ms, server_revision, conflict_choice_key
         FROM audiobook_progress WHERE user_id = ? AND book_id = ?`,
      )
      .get(activeUserId, bookId) as ProgressRow | undefined;
    return row ? mapRow(row) : null;
  },

  upsertProgress(
    progress: AudiobookProgress,
    pendingSync: boolean,
    options?: { conflictChoiceKey?: string | null },
  ): void {
    const userId = requireUserId();
    const existing = ProgressDb.getProgress(progress.bookId);
    const choiceKey =
      options && "conflictChoiceKey" in options
        ? options.conflictChoiceKey ?? null
        : (existing?.conflictChoiceKey ?? null);
    getDb()
      .prepare(
        `INSERT INTO audiobook_progress (
           user_id, book_id, track_id, position_ms, updated_at, pending_sync, revision,
           server_track_id, server_position_ms, server_revision, conflict_choice_key
         ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
         ON CONFLICT(user_id, book_id) DO UPDATE SET
           track_id = excluded.track_id,
           position_ms = excluded.position_ms,
           updated_at = excluded.updated_at,
           pending_sync = excluded.pending_sync,
           revision = excluded.revision,
           server_track_id = excluded.server_track_id,
           server_position_ms = excluded.server_position_ms,
           server_revision = excluded.server_revision,
           conflict_choice_key = excluded.conflict_choice_key`,
      )
      .run(
        userId,
        progress.bookId,
        progress.trackId,
        progress.positionMs,
        progress.updatedAt,
        pendingSync ? 1 : 0,
        progress.revision,
        progress.serverTrackId ?? null,
        progress.serverPositionMs ?? null,
        progress.serverRevision ?? null,
        choiceKey,
      );
  },

  updateServerSnapshot(
    bookId: string,
    snapshot: { trackId: string; positionMs: number; revision: number; updatedAt: string },
  ): StoredProgress | null {
    const userId = requireUserId();
    const local = ProgressDb.getProgress(bookId);
    if (!local) {
      const created: AudiobookProgress = {
        bookId,
        trackId: snapshot.trackId,
        positionMs: snapshot.positionMs,
        updatedAt: snapshot.updatedAt,
        revision: snapshot.revision,
        serverTrackId: snapshot.trackId,
        serverPositionMs: snapshot.positionMs,
        serverRevision: snapshot.revision,
      };
      ProgressDb.upsertProgress(created, false, { conflictChoiceKey: null });
      return ProgressDb.getProgress(bookId);
    }
    getDb()
      .prepare(
        `UPDATE audiobook_progress SET
           server_track_id = ?, server_position_ms = ?, server_revision = ?,
           conflict_choice_key = NULL
         WHERE user_id = ? AND book_id = ?`,
      )
      .run(snapshot.trackId, snapshot.positionMs, snapshot.revision, userId, bookId);
    // Only clear choice key when snapshot values actually change vs previous
    return ProgressDb.getProgress(bookId);
  },

  applyServerToPlayHead(
    bookId: string,
    snapshot: { trackId: string; positionMs: number; revision: number; updatedAt: string },
    conflictChoiceKey: string | null,
  ): StoredProgress {
    const applied: AudiobookProgress = {
      bookId,
      trackId: snapshot.trackId,
      positionMs: snapshot.positionMs,
      updatedAt: snapshot.updatedAt,
      revision: snapshot.revision,
      serverTrackId: snapshot.trackId,
      serverPositionMs: snapshot.positionMs,
      serverRevision: snapshot.revision,
    };
    ProgressDb.upsertProgress(applied, false, { conflictChoiceKey });
    return ProgressDb.getProgress(bookId)!;
  },

  getPendingProgress(): StoredProgress[] {
    if (!activeUserId) return [];
    const rows = getDb()
      .prepare(
        `SELECT user_id, book_id, track_id, position_ms, updated_at, pending_sync, revision,
                server_track_id, server_position_ms, server_revision, conflict_choice_key
         FROM audiobook_progress WHERE user_id = ? AND pending_sync = 1`,
      )
      .all(activeUserId) as ProgressRow[];
    return rows.map(mapRow);
  },

  markProgressSynced(bookId: string, revision: number): void {
    const userId = requireUserId();
    getDb()
      .prepare(
        `UPDATE audiobook_progress SET pending_sync = 0, revision = ? WHERE user_id = ? AND book_id = ?`,
      )
      .run(revision, userId, bookId);
  },

  setConflictChoiceKey(bookId: string, key: string | null): void {
    const userId = requireUserId();
    getDb()
      .prepare(
        `UPDATE audiobook_progress SET conflict_choice_key = ? WHERE user_id = ? AND book_id = ?`,
      )
      .run(key, userId, bookId);
  },

  deleteProgressForUser(userId: string): void {
    getDb().prepare(`DELETE FROM audiobook_progress WHERE user_id = ?`).run(userId);
  },

  getPendingSyncCount(): number {
    if (!activeUserId) return 0;
    const progress = getDb()
      .prepare(
        `SELECT COUNT(*) as count FROM audiobook_progress WHERE user_id = ? AND pending_sync = 1`,
      )
      .get(activeUserId) as { count: number };
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

  getHydratedUserId(): string | null {
    const row = getDb()
      .prepare(`SELECT value FROM app_meta WHERE key = 'progress_hydrated_user_id'`)
      .get() as { value: string } | undefined;
    return row?.value || null;
  },

  setHydratedUserId(userId: string | null): void {
    if (!userId) {
      getDb().prepare(`DELETE FROM app_meta WHERE key = 'progress_hydrated_user_id'`).run();
      return;
    }
    getDb()
      .prepare(
        `INSERT INTO app_meta (key, value) VALUES ('progress_hydrated_user_id', @value)
         ON CONFLICT(key) DO UPDATE SET value = excluded.value`,
      )
      .run({ value: userId });
  },
};
