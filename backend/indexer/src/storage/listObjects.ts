import type pg from "pg";

export interface StorageObjectRow {
  name: string;
  sizeBytes: number | null;
}

export async function listContentObjects(pool: pg.Pool): Promise<StorageObjectRow[]> {
  const result = await pool.query<{ name: string; metadata: { size?: number } | null }>(
    `SELECT name, metadata
     FROM storage.objects
     WHERE bucket_id = 'content'
       AND (name LIKE 'cycles/%' OR name LIKE 'music/%')`,
  );

  return result.rows.map((row) => ({
    name: row.name,
    sizeBytes: typeof row.metadata?.size === "number" ? row.metadata.size : null,
  }));
}
