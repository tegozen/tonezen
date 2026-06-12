import type pg from "pg";

export class CatalogRepository {
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
}
