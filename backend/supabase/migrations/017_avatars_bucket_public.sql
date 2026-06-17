-- Avatars are served via /storage/v1/object/public/avatars/...
UPDATE storage.buckets SET public = true WHERE id = 'avatars';

INSERT INTO storage.buckets (id, name, public)
VALUES ('avatars', 'avatars', true)
ON CONFLICT (id) DO UPDATE SET public = EXCLUDED.public;
