-- Public avatars bucket: users upload to {user_id}/avatar.jpg

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'avatars',
  'avatars',
  true,
  2097152,
  ARRAY['image/jpeg', 'image/png', 'image/webp']
)
ON CONFLICT (id) DO NOTHING;

CREATE POLICY "avatars_select"
ON storage.objects FOR SELECT
TO anon, authenticated, service_role
USING (bucket_id = 'avatars');

CREATE POLICY "avatars_insert_own"
ON storage.objects FOR INSERT
TO authenticated, service_role
WITH CHECK (
  bucket_id = 'avatars'
  AND auth.uid()::text = (storage.foldername(name))[1]
);

CREATE POLICY "avatars_update_own"
ON storage.objects FOR UPDATE
TO authenticated, service_role
USING (
  bucket_id = 'avatars'
  AND auth.uid()::text = (storage.foldername(name))[1]
)
WITH CHECK (
  bucket_id = 'avatars'
  AND auth.uid()::text = (storage.foldername(name))[1]
);

CREATE POLICY "avatars_delete_own"
ON storage.objects FOR DELETE
TO authenticated, service_role
USING (
  bucket_id = 'avatars'
  AND auth.uid()::text = (storage.foldername(name))[1]
);
