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
    const result = await this.pool.query(
      `SELECT c.id, c.slug, c.title, c.book_order,
              b.id AS book_id, b.slug AS book_slug, b.content_type,
              b.title AS book_title, b.author, b.cover_path, cb.sort_order
       FROM cycles c
       LEFT JOIN cycle_books cb ON cb.cycle_id = c.id
       LEFT JOIN books b ON b.id = cb.book_id AND b.deleted_at IS NULL
       ${where}
       ORDER BY c.title, cb.sort_order`,
      params,
    );

    const cycles: Array<{
      id: string;
      slug: string;
      title: string;
      book_order: unknown;
      books: Array<{
        id: string;
        slug: string;
        content_type: string;
        title: string;
        author: string | null;
        cover_path: string | null;
      }>;
    }> = [];
    const cycleIndex = new Map<string, number>();

    for (const row of result.rows) {
      let index = cycleIndex.get(row.id);
      if (index === undefined) {
        index = cycles.length;
        cycleIndex.set(row.id, index);
        cycles.push({
          id: row.id,
          slug: row.slug,
          title: row.title,
          book_order: row.book_order,
          books: [],
        });
      }
      if (row.book_id) {
        cycles[index].books.push({
          id: row.book_id,
          slug: row.book_slug,
          content_type: row.content_type,
          title: row.book_title,
          author: row.author,
          cover_path: row.cover_path,
        });
      }
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
      `SELECT t.id, t.sort_order, t.title, t.artist, t.filename, t.duration_ms, tf.waveform_peaks
       FROM tracks t
       LEFT JOIN track_files tf ON tf.track_id = t.id
       WHERE t.book_id = $1 AND t.deleted_at IS NULL
       ORDER BY t.sort_order`,
      [bookId],
    );
    const tracks = tracksResult.rows.map((row) => ({
      id: row.id,
      sort_order: row.sort_order,
      title: row.title,
      artist: row.artist,
      filename: row.filename,
      duration_ms: row.duration_ms,
      waveform_peaks: sanitizeWaveformPeaks(row.waveform_peaks),
    }));
    return { ...bookResult.rows[0], tracks };
  }

  async getAllTracks(updatedSince?: string) {
    const params: unknown[] = [];
    let where = "WHERE t.deleted_at IS NULL AND b.deleted_at IS NULL";
    if (updatedSince) {
      params.push(updatedSince);
      where += ` AND t.updated_at > $${params.length}`;
    }
    const result = await this.pool.query(
      `SELECT t.id, t.book_id, t.sort_order, t.title, t.artist, t.filename, t.duration_ms,
              tf.waveform_peaks
       FROM tracks t
       INNER JOIN books b ON b.id = t.book_id
       LEFT JOIN track_files tf ON tf.track_id = t.id
       ${where}
       ORDER BY t.book_id, t.sort_order`,
      params,
    );
    return result.rows.map((row) => ({
      id: row.id,
      book_id: row.book_id,
      sort_order: row.sort_order,
      title: row.title,
      artist: row.artist,
      filename: row.filename,
      duration_ms: row.duration_ms,
      waveform_peaks: sanitizeWaveformPeaks(row.waveform_peaks),
    }));
  }
}

function sanitizeWaveformPeaks(value: unknown): number[] | null {
  return Array.isArray(value) &&
    value.length === 64 &&
    value.every((peak) => Number.isInteger(peak) && peak >= 0 && peak <= 100)
    ? value
    : null;
}
