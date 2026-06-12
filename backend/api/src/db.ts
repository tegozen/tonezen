import type pg from "pg";
import { mergeProgressLww, type ProgressRecord } from "./lib/progressLww.js";

export class ApiRepository {
  constructor(private pool: pg.Pool) {}

  async getCycles(updatedSince?: string) {
    const params: unknown[] = [];
    let where = "WHERE c.deleted_at IS NULL";
    if (updatedSince) {
      params.push(updatedSince);
      where += ` AND c.updated_at > $${params.length}`;
    }
    const cyclesResult = await this.pool.query(
      `SELECT c.id, c.slug, c.title, c.book_order FROM cycles c ${where} ORDER BY c.title`,
      params,
    );
    const cycles = [];
    for (const row of cyclesResult.rows) {
      const booksResult = await this.pool.query(
        `SELECT b.id, b.slug, b.content_type, b.title, b.author, b.cover_path
         FROM books b
         JOIN cycle_books cb ON cb.book_id = b.id
         WHERE cb.cycle_id = $1 AND b.deleted_at IS NULL
         ORDER BY cb.sort_order`,
        [row.id],
      );
      cycles.push({
        id: row.id,
        slug: row.slug,
        title: row.title,
        book_order: row.book_order,
        books: booksResult.rows,
      });
    }
    return cycles;
  }

  async getMusicAlbums(updatedSince?: string) {
    const params: unknown[] = [];
    let where = "WHERE content_type = 'music' AND deleted_at IS NULL";
    if (updatedSince) {
      params.push(updatedSince);
      where += ` AND updated_at > $${params.length}`;
    }
    const result = await this.pool.query(
      `SELECT id, slug, content_type, title, author, cover_path FROM books ${where} ORDER BY title`,
      params,
    );
    return result.rows;
  }

  async getBookDetail(bookId: string) {
    const bookResult = await this.pool.query(
      `SELECT id, slug, content_type, title, author, cover_path
       FROM books WHERE id = $1 AND deleted_at IS NULL`,
      [bookId],
    );
    if (bookResult.rows.length === 0) return null;
    const tracksResult = await this.pool.query(
      `SELECT t.id, t.sort_order, t.title, t.filename, t.duration_ms, tf.storage_path
       FROM tracks t
       LEFT JOIN track_files tf ON tf.track_id = t.id
       WHERE t.book_id = $1 AND t.deleted_at IS NULL
       ORDER BY t.sort_order`,
      [bookId],
    );
    return { ...bookResult.rows[0], tracks: tracksResult.rows };
  }

  async getTrackStoragePaths(trackIds: string[]) {
    const result = await this.pool.query(
      `SELECT t.id as track_id, tf.storage_path
       FROM tracks t
       JOIN track_files tf ON tf.track_id = t.id
       WHERE t.id = ANY($1::uuid[]) AND t.deleted_at IS NULL`,
      [trackIds],
    );
    return result.rows as { track_id: string; storage_path: string }[];
  }

  async getFavorites(userId: string) {
    const result = await this.pool.query(
      `SELECT f.book_id, f.created_at, b.title, b.content_type
       FROM favorites f JOIN books b ON b.id = f.book_id
       WHERE f.user_id = $1`,
      [userId],
    );
    return result.rows;
  }

  async addFavorite(userId: string, bookId: string) {
    await this.pool.query(
      `INSERT INTO favorites (user_id, book_id) VALUES ($1, $2) ON CONFLICT DO NOTHING`,
      [userId, bookId],
    );
  }

  async removeFavorite(userId: string, bookId: string) {
    await this.pool.query(`DELETE FROM favorites WHERE user_id = $1 AND book_id = $2`, [
      userId,
      bookId,
    ]);
  }

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
    if (existing.rows.length > 0) {
      const remote = existing.rows[0] as ProgressRecord;
      const winner = mergeProgressLww(incoming, remote);
      if (winner !== incoming) {
        return { skipped: true as const, progress: winner };
      }
    }

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
