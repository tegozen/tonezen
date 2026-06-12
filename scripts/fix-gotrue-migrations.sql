-- Skip GoTrue backfill migrations that fail when auth.identities.id is uuid.
INSERT INTO auth.schema_migrations (version)
SELECT version
FROM (
  VALUES
    ('20221208132122')
) AS skipped(version)
WHERE EXISTS (
  SELECT 1
  FROM information_schema.tables
  WHERE table_schema = 'auth'
    AND table_name = 'schema_migrations'
)
ON CONFLICT DO NOTHING;
