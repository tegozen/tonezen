-- Public avatars bucket: users upload to {user_id}/avatar.jpg
-- Idempotent policies for re-run safety on initdb.

INSERT INTO storage.buckets (id, name)
VALUES ('avatars', 'avatars')
ON CONFLICT (id) DO NOTHING;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'storage' AND tablename = 'objects' AND policyname = 'avatars_select'
  ) THEN
    CREATE POLICY "avatars_select"
    ON storage.objects FOR SELECT
    TO anon, authenticated, service_role
    USING (bucket_id = 'avatars');
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'storage' AND tablename = 'objects' AND policyname = 'avatars_insert_own'
  ) THEN
    CREATE POLICY "avatars_insert_own"
    ON storage.objects FOR INSERT
    TO authenticated, service_role
    WITH CHECK (
      bucket_id = 'avatars'
      AND auth.uid()::text = (storage.foldername(name))[1]
    );
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'storage' AND tablename = 'objects' AND policyname = 'avatars_update_own'
  ) THEN
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
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'storage' AND tablename = 'objects' AND policyname = 'avatars_delete_own'
  ) THEN
    CREATE POLICY "avatars_delete_own"
    ON storage.objects FOR DELETE
    TO authenticated, service_role
    USING (
      bucket_id = 'avatars'
      AND auth.uid()::text = (storage.foldername(name))[1]
    );
  END IF;
END $$;
