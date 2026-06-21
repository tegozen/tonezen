import type pg from "pg";

const WATERMARK_KEY = "object_watermark";
const EPOCH = new Date("1970-01-01T00:00:00.000Z");

export async function readWatermark(pool: pg.Pool): Promise<Date> {
  const result = await pool.query<{ timestamptz_value: Date | null }>(
    `SELECT timestamptz_value FROM indexer_state WHERE key = $1`,
    [WATERMARK_KEY],
  );

  const stored = result.rows[0]?.timestamptz_value ?? null;
  if (stored != null) {
    return stored;
  }

  const trackFiles = await pool.query<{ max_updated_at: Date | null; count: string }>(
    `SELECT MAX(storage_object_updated_at) AS max_updated_at, COUNT(*)::text AS count
     FROM track_files`,
  );

  const count = Number(trackFiles.rows[0]?.count ?? 0);
  if (count > 0) {
    return trackFiles.rows[0]?.max_updated_at ?? EPOCH;
  }

  return EPOCH;
}

export async function writeWatermark(pool: pg.Pool, watermark: Date): Promise<void> {
  await pool.query(
    `INSERT INTO indexer_state (key, timestamptz_value)
     VALUES ($1, $2)
     ON CONFLICT (key) DO UPDATE SET timestamptz_value = EXCLUDED.timestamptz_value`,
    [WATERMARK_KEY, watermark],
  );
}

export function maxUpdatedAt(
  objects: { updatedAt: Date | null; catalogUpdatedAt?: Date | null }[],
): Date {
  let max = EPOCH;
  for (const object of objects) {
    const updatedAt = object.catalogUpdatedAt ?? object.updatedAt;
    if (updatedAt != null && updatedAt > max) {
      max = updatedAt;
    }
  }
  return max > EPOCH ? max : new Date();
}
