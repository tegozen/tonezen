-- Idempotent post-storage-api bootstrap (do not edit backend/supabase/migrations).

GRANT authenticator TO supabase_storage_admin;
REVOKE anon, authenticated, service_role FROM supabase_storage_admin;

ALTER ROLE supabase_storage_admin SET search_path TO storage, public;

GRANT USAGE ON SCHEMA storage TO anon, authenticated, service_role;
GRANT SELECT ON ALL TABLES IN SCHEMA storage TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA storage
  GRANT SELECT ON TABLES TO anon, authenticated, service_role;

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('content', 'content', false, 524288000, NULL)
ON CONFLICT (id) DO NOTHING;
