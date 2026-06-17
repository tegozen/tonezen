import type pg from "pg";

export interface StorageObjectRow {
  name: string;
  sizeBytes: number | null;
  updatedAt: Date | null;
}

interface StorageObjectDbRow {
  name: string;
  metadata: { size?: number } | null;
  updated_at: Date | null;
}

function mapStorageRow(row: StorageObjectDbRow): StorageObjectRow {
  return {
    name: row.name,
    sizeBytes: typeof row.metadata?.size === "number" ? row.metadata.size : null,
    updatedAt: row.updated_at,
  };
}

export async function listContentObjects(pool: pg.Pool): Promise<StorageObjectRow[]> {
  const result = await pool.query<StorageObjectDbRow>(
    `SELECT name, metadata, updated_at
     FROM storage.objects
     WHERE bucket_id = 'content'
       AND (name LIKE 'cycles/%' OR name LIKE 'music/%')`,
  );

  return result.rows.map(mapStorageRow);
}

export async function listChangedObjects(
  pool: pg.Pool,
  watermark: Date,
): Promise<StorageObjectRow[]> {
  const result = await pool.query<StorageObjectDbRow>(
    `SELECT o.name, o.metadata, o.updated_at
     FROM storage.objects o
     WHERE o.bucket_id = 'content'
       AND (o.name LIKE 'cycles/%' OR o.name LIKE 'music/%')
       AND (
         o.updated_at > $1
         OR NOT EXISTS (
           SELECT 1 FROM track_files tf WHERE tf.storage_path = o.name
         )
       )`,
    [watermark],
  );

  return result.rows.map(mapStorageRow);
}
