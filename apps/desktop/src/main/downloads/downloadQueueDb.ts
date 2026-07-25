import { getDb } from "../db/connection.js";

export interface DownloadQueueRow {
  bookId: string;
  trackId: string;
  priority: string;
  batchId: string | null;
  enqueuedAt: number;
  title: string;
  subtitle: string | null;
  contentType: string;
  status: string;
  bytesDownloaded: number;
  totalBytes: number | null;
  tempPath: string | null;
}

interface DownloadQueueDbRow {
  book_id: string;
  track_id: string;
  priority: string;
  batch_id: string | null;
  enqueued_at: number;
  title: string;
  subtitle: string | null;
  content_type: string;
  status: string;
  bytes_downloaded: number;
  total_bytes: number | null;
  temp_path: string | null;
}

function mapRow(row: DownloadQueueDbRow): DownloadQueueRow {
  return {
    bookId: row.book_id,
    trackId: row.track_id,
    priority: row.priority,
    batchId: row.batch_id,
    enqueuedAt: row.enqueued_at,
    title: row.title,
    subtitle: row.subtitle,
    contentType: row.content_type,
    status: row.status,
    bytesDownloaded: row.bytes_downloaded,
    totalBytes: row.total_bytes,
    tempPath: row.temp_path,
  };
}

export const DownloadQueueDb = {
  getAll(): DownloadQueueRow[] {
    const rows = getDb()
      .prepare(
        `SELECT book_id, track_id, priority, batch_id, enqueued_at, title, subtitle,
                content_type, status, bytes_downloaded, total_bytes, temp_path
         FROM download_queue
         ORDER BY enqueued_at ASC`,
      )
      .all() as DownloadQueueDbRow[];
    return rows.map(mapRow);
  },

  get(bookId: string, trackId: string): DownloadQueueRow | null {
    const row = getDb()
      .prepare(
        `SELECT book_id, track_id, priority, batch_id, enqueued_at, title, subtitle,
                content_type, status, bytes_downloaded, total_bytes, temp_path
         FROM download_queue
         WHERE book_id = ? AND track_id = ?
         LIMIT 1`,
      )
      .get(bookId, trackId) as DownloadQueueDbRow | undefined;
    return row ? mapRow(row) : null;
  },

  upsert(item: DownloadQueueRow): void {
    getDb()
      .prepare(
        `INSERT INTO download_queue (
           book_id, track_id, priority, batch_id, enqueued_at, title, subtitle,
           content_type, status, bytes_downloaded, total_bytes, temp_path
         ) VALUES (
           @bookId, @trackId, @priority, @batchId, @enqueuedAt, @title, @subtitle,
           @contentType, @status, @bytesDownloaded, @totalBytes, @tempPath
         )
         ON CONFLICT(book_id, track_id) DO UPDATE SET
           priority = excluded.priority,
           batch_id = excluded.batch_id,
           enqueued_at = excluded.enqueued_at,
           title = excluded.title,
           subtitle = excluded.subtitle,
           content_type = excluded.content_type,
           status = excluded.status,
           bytes_downloaded = excluded.bytes_downloaded,
           total_bytes = excluded.total_bytes,
           temp_path = excluded.temp_path`,
      )
      .run(item);
  },

  delete(bookId: string, trackId: string): void {
    getDb()
      .prepare(`DELETE FROM download_queue WHERE book_id = ? AND track_id = ?`)
      .run(bookId, trackId);
  },

  deleteAll(): void {
    getDb().prepare(`DELETE FROM download_queue`).run();
  },

  updateProgress(
    bookId: string,
    trackId: string,
    bytesDownloaded: number,
    totalBytes: number | null,
    tempPath: string | null,
  ): void {
    getDb()
      .prepare(
        `UPDATE download_queue
         SET bytes_downloaded = ?, total_bytes = ?, temp_path = ?
         WHERE book_id = ? AND track_id = ?`,
      )
      .run(bytesDownloaded, totalBytes, tempPath, bookId, trackId);
  },
};
