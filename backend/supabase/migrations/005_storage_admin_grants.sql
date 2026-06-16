-- Fix storage-api role impersonation (supabase/storage#369)
-- Without authenticator grant, Studio cannot list buckets (42501 on set_config role).

GRANT authenticator TO supabase_storage_admin;
REVOKE anon, authenticated, service_role FROM supabase_storage_admin;

ALTER ROLE supabase_storage_admin SET search_path TO storage, public;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.schemata WHERE schema_name = 'storage'
  ) THEN
    GRANT USAGE ON SCHEMA storage TO anon, authenticated, service_role;
    GRANT SELECT ON ALL TABLES IN SCHEMA storage TO anon, authenticated, service_role;
    ALTER DEFAULT PRIVILEGES IN SCHEMA storage
      GRANT SELECT ON TABLES TO anon, authenticated, service_role;
  END IF;
END $$;
