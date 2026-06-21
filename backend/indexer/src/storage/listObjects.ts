import type pg from "pg";

export interface StorageObjectRow {
  name: string;
  displayPath?: string | null;
  sizeBytes: number | null;
  updatedAt: Date | null;
  catalogUpdatedAt?: Date | null;
}

interface StorageObjectDbRow {
  name: string;
  display_path: string | null;
  metadata: { size?: number } | null;
  updated_at: Date | null;
  catalog_updated_at: Date | null;
}

function mapStorageRow(row: StorageObjectDbRow): StorageObjectRow {
  return {
    name: row.name,
    displayPath: row.display_path,
    sizeBytes: typeof row.metadata?.size === "number" ? row.metadata.size : null,
    updatedAt: row.updated_at,
    catalogUpdatedAt: row.catalog_updated_at,
  };
}

export async function listContentObjects(pool: pg.Pool): Promise<StorageObjectRow[]> {
  const result = await pool.query<StorageObjectDbRow>(
    `SELECT o.name, cdn.display_path, o.metadata, o.updated_at,
            GREATEST(o.updated_at, COALESCE(cdn.updated_at, o.updated_at)) AS catalog_updated_at
     FROM storage.objects o
     LEFT JOIN content_display_names cdn ON cdn.storage_path = o.name
     WHERE o.bucket_id = 'content'
       AND (o.name LIKE 'cycles/%' OR o.name LIKE 'music/%')`,
  );

  return result.rows.map(mapStorageRow);
}

export async function listChangedObjects(
  pool: pg.Pool,
  watermark: Date,
): Promise<StorageObjectRow[]> {
  const result = await pool.query<StorageObjectDbRow>(
    `SELECT o.name, cdn.display_path, o.metadata, o.updated_at,
            GREATEST(o.updated_at, COALESCE(cdn.updated_at, o.updated_at)) AS catalog_updated_at
     FROM storage.objects o
     LEFT JOIN content_display_names cdn ON cdn.storage_path = o.name
     WHERE o.bucket_id = 'content'
       AND (o.name LIKE 'cycles/%' OR o.name LIKE 'music/%')
       AND (
         o.updated_at > $1
         OR cdn.updated_at > $1
         OR NOT EXISTS (
           SELECT 1 FROM track_files tf WHERE tf.storage_path = o.name
         )
       )`,
    [watermark],
  );

  return result.rows.map(mapStorageRow);
}
