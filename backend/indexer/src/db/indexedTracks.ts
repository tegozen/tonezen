import type pg from "pg";

export interface IndexedTrackRow {
  storagePath: string;
  checksum: string | null;
  sizeBytes: number | null;
  waveformPeaks: unknown;
  storageObjectUpdatedAt: Date | null;
  title: string;
  artist: string | null;
  durationMs: number | null;
}

interface IndexedTrackDbRow {
  storage_path: string;
  checksum: string | null;
  size_bytes: string | number | null;
  waveform_peaks: unknown;
  storage_object_updated_at: Date | null;
  title: string;
  artist: string | null;
  duration_ms: number | null;
}

export async function loadIndexedTracks(pool: pg.Pool): Promise<Map<string, IndexedTrackRow>> {
  const result = await pool.query<IndexedTrackDbRow>(
    `SELECT tf.storage_path, tf.checksum, tf.size_bytes, tf.waveform_peaks,
            tf.storage_object_updated_at, t.title, t.artist, t.duration_ms
     FROM track_files tf
     JOIN tracks t ON t.id = tf.track_id
     WHERE t.deleted_at IS NULL`,
  );

  const map = new Map<string, IndexedTrackRow>();
  for (const row of result.rows) {
    map.set(row.storage_path, {
      storagePath: row.storage_path,
      checksum: row.checksum,
      sizeBytes: row.size_bytes != null ? Number(row.size_bytes) : null,
      waveformPeaks: row.waveform_peaks,
      storageObjectUpdatedAt: row.storage_object_updated_at,
      title: row.title,
      artist: row.artist,
      durationMs: row.duration_ms,
    });
  }
  return map;
}
