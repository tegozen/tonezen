-- Idempotent post-storage-api bootstrap (do not edit backend/supabase/migrations).

GRANT SELECT, INSERT, UPDATE, DELETE ON storage.objects TO authenticated, service_role;
GRANT SELECT ON storage.objects TO anon;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'storage' AND tablename = 'objects' AND policyname = 'content_objects_select'
  ) THEN
    CREATE POLICY "content_objects_select"
    ON storage.objects FOR SELECT
    TO anon, authenticated, service_role
    USING (bucket_id = 'content');
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'storage' AND tablename = 'objects' AND policyname = 'content_objects_insert'
  ) THEN
    CREATE POLICY "content_objects_insert"
    ON storage.objects FOR INSERT
    TO authenticated, service_role
    WITH CHECK (bucket_id = 'content');
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'storage' AND tablename = 'objects' AND policyname = 'content_objects_update'
  ) THEN
    CREATE POLICY "content_objects_update"
    ON storage.objects FOR UPDATE
    TO authenticated, service_role
    USING (bucket_id = 'content')
    WITH CHECK (bucket_id = 'content');
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'storage' AND tablename = 'objects' AND policyname = 'content_objects_delete'
  ) THEN
    CREATE POLICY "content_objects_delete"
    ON storage.objects FOR DELETE
    TO authenticated, service_role
    USING (bucket_id = 'content');
  END IF;
END $$;
