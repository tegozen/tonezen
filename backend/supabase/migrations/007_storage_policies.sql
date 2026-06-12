-- Self-hosted Storage: RLS is enabled but defaults deny all without policies.
-- Storage schema init grants SELECT only; uploads need INSERT/UPDATE/DELETE too.

GRANT SELECT, INSERT, UPDATE, DELETE ON storage.objects TO authenticated, service_role;
GRANT SELECT ON storage.objects TO anon;

CREATE POLICY "content_objects_select"
ON storage.objects FOR SELECT
TO anon, authenticated, service_role
USING (bucket_id = 'content');

CREATE POLICY "content_objects_insert"
ON storage.objects FOR INSERT
TO authenticated, service_role
WITH CHECK (bucket_id = 'content');

CREATE POLICY "content_objects_update"
ON storage.objects FOR UPDATE
TO authenticated, service_role
USING (bucket_id = 'content')
WITH CHECK (bucket_id = 'content');

CREATE POLICY "content_objects_delete"
ON storage.objects FOR DELETE
TO authenticated, service_role
USING (bucket_id = 'content');
