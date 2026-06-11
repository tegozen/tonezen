-- Supabase Storage bucket for audio content (upload via Studio)

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = 'storage' AND table_name = 'buckets'
  ) THEN
    INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
    VALUES ('content', 'content', false, 524288000, NULL)
    ON CONFLICT (id) DO NOTHING;
  ELSE
    RAISE NOTICE 'storage.buckets not found — storage schema will be initialized by supabase/postgres image';
  END IF;
END $$;
