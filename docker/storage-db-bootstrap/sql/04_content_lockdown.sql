-- Idempotent post-storage-api bootstrap (do not edit backend/supabase/migrations).

DROP POLICY IF EXISTS "content_objects_select" ON storage.objects;
DROP POLICY IF EXISTS "content_objects_insert" ON storage.objects;
DROP POLICY IF EXISTS "content_objects_update" ON storage.objects;
DROP POLICY IF EXISTS "content_objects_delete" ON storage.objects;

CREATE POLICY "content_objects_select"
ON storage.objects FOR SELECT
TO service_role
USING (bucket_id = 'content');

CREATE POLICY "content_objects_insert"
ON storage.objects FOR INSERT
TO service_role
WITH CHECK (bucket_id = 'content');

CREATE POLICY "content_objects_update"
ON storage.objects FOR UPDATE
TO service_role
USING (bucket_id = 'content')
WITH CHECK (bucket_id = 'content');

CREATE POLICY "content_objects_delete"
ON storage.objects FOR DELETE
TO service_role
USING (bucket_id = 'content');

REVOKE SELECT ON track_files FROM anon;
