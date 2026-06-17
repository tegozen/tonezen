-- Indexer incremental state: storage object watermark + per-file object updated_at

ALTER TABLE track_files
  ADD COLUMN IF NOT EXISTS storage_object_updated_at TIMESTAMPTZ;

UPDATE track_files tf
SET storage_object_updated_at = o.updated_at
FROM storage.objects o
WHERE o.bucket_id = 'content'
  AND o.name = tf.storage_path
  AND tf.storage_object_updated_at IS NULL;

CREATE TABLE IF NOT EXISTS indexer_state (
  key TEXT PRIMARY KEY,
  timestamptz_value TIMESTAMPTZ
);

INSERT INTO indexer_state (key, timestamptz_value)
VALUES ('object_watermark', NULL)
ON CONFLICT (key) DO NOTHING;

-- Indexer service role needs read/write on indexer_state (runs as supabase_admin via DATABASE_URL)
GRANT SELECT, INSERT, UPDATE ON indexer_state TO service_role;
