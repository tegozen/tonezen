import type pg from "pg";
import {
  maybeProgressCasConflict,
  type ProgressRecord,
} from "../lib/progressCas.js";

export class ProgressRepository {
  constructor(private pool: pg.Pool) {}

  async getAudiobookProgress(userId: string) {
    const result = await this.pool.query(
      `SELECT book_id, track_id, position_ms, updated_at, revision
       FROM audiobook_progress WHERE user_id = $1`,
      [userId],
    );
    return result.rows;
  }

  async upsertAudiobookProgress(
    userId: string,
    bookId: string,
    trackId: string,
    positionMs: number,
    baseRevision: number,
  ) {
    const bookCheck = await this.pool.query(
      `SELECT content_type FROM books WHERE id = $1 AND deleted_at IS NULL`,
      [bookId],
    );
    if (bookCheck.rows.length === 0) return { error: "not_found" as const };
    if (bookCheck.rows[0].content_type !== "audiobook") {
      return { error: "not_audiobook" as const };
    }

    const trackCheck = await this.pool.query(
      `SELECT 1 FROM tracks WHERE id = $1 AND book_id = $2 AND deleted_at IS NULL`,
      [trackId, bookId],
    );
    if (trackCheck.rows.length === 0) {
      return { error: "invalid_track" as const };
    }

    const client = await this.pool.connect();
    try {
      await client.query("BEGIN");
      const existing = await client.query(
        `SELECT book_id, track_id, position_ms, updated_at, revision
         FROM audiobook_progress WHERE user_id = $1 AND book_id = $2
         FOR UPDATE`,
        [userId, bookId],
      );
      const current = existing.rows[0] as ProgressRecord | undefined;

      if (!current) {
        if (baseRevision !== 0) {
          await client.query("ROLLBACK");
          return { error: "cas_conflict" as const };
        }
        const inserted = await client.query(
          `INSERT INTO audiobook_progress (
             user_id, book_id, track_id, position_ms, updated_at, revision
           ) VALUES ($1, $2, $3, $4, now(), 1)
           RETURNING book_id, track_id, position_ms, updated_at, revision`,
          [userId, bookId, trackId, positionMs],
        );
        await client.query("COMMIT");
        return { progress: inserted.rows[0] };
      }

      const conflict = maybeProgressCasConflict(baseRevision, current);
      if (conflict) {
        await client.query("ROLLBACK");
        return conflict;
      }

      const updated = await client.query(
        `UPDATE audiobook_progress SET
           track_id = $3,
           position_ms = $4,
           updated_at = now(),
           revision = revision + 1
         WHERE user_id = $1 AND book_id = $2
         RETURNING book_id, track_id, position_ms, updated_at, revision`,
        [userId, bookId, trackId, positionMs],
      );
      await client.query("COMMIT");
      return { progress: updated.rows[0] };
    } catch (err) {
      try {
        await client.query("ROLLBACK");
      } catch {
        /* ignore */
      }
      throw err;
    } finally {
      client.release();
    }
  }
}
