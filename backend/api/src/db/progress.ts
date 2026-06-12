import type pg from "pg";
import { maybeSkipProgressWrite, type ProgressRecord } from "../lib/progressLww.js";

export class ProgressRepository {
  constructor(private pool: pg.Pool) {}

  async getAudiobookProgress(userId: string) {
    const result = await this.pool.query(
      `SELECT book_id, track_id, position_ms, updated_at
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
    updatedAt: string,
  ) {
    const bookCheck = await this.pool.query(
      `SELECT content_type FROM books WHERE id = $1 AND deleted_at IS NULL`,
      [bookId],
    );
    if (bookCheck.rows.length === 0) return { error: "not_found" as const };
    if (bookCheck.rows[0].content_type !== "audiobook") {
      return { error: "not_audiobook" as const };
    }

    const existing = await this.pool.query(
      `SELECT book_id, track_id, position_ms, updated_at
       FROM audiobook_progress WHERE user_id = $1 AND book_id = $2`,
      [userId, bookId],
    );
    const incoming: ProgressRecord = {
      book_id: bookId,
      track_id: trackId,
      position_ms: positionMs,
      updated_at: updatedAt,
    };
    const skipResult = maybeSkipProgressWrite(
      incoming,
      existing.rows[0] as ProgressRecord | undefined,
    );
    if (skipResult) return skipResult;

    const result = await this.pool.query(
      `INSERT INTO audiobook_progress (user_id, book_id, track_id, position_ms, updated_at)
       VALUES ($1, $2, $3, $4, $5)
       ON CONFLICT (user_id, book_id) DO UPDATE SET
         track_id = EXCLUDED.track_id,
         position_ms = EXCLUDED.position_ms,
         updated_at = EXCLUDED.updated_at
       WHERE audiobook_progress.updated_at <= EXCLUDED.updated_at
       RETURNING book_id, track_id, position_ms, updated_at`,
      [userId, bookId, trackId, positionMs, updatedAt],
    );
    return { progress: result.rows[0] ?? existing.rows[0] };
  }
}
