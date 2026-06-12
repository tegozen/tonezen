import type pg from "pg";

export class DownloadsRepository {
  constructor(private pool: pg.Pool) {}

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
}
