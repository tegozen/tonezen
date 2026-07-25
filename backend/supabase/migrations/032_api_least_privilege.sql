-- Least-privilege API DB role + catalog/storage hardening.
-- API previously connected as supabase_admin (bypasses RLS + superuser).
-- tonezen_api still bypasses RLS (app enforces user scoping) but has no
-- superuser privileges and only the grants needed by backend/api.

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'tonezen_api') THEN
    CREATE ROLE tonezen_api LOGIN
      NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
  END IF;
END $$;

ALTER ROLE tonezen_api WITH BYPASSRLS;

DO $$
BEGIN
  EXECUTE format('GRANT CONNECT ON DATABASE %I TO tonezen_api', current_database());
END $$;
GRANT USAGE ON SCHEMA public TO tonezen_api;

GRANT SELECT ON cycles, books, cycle_books, tracks, track_files TO tonezen_api;
GRANT SELECT, INSERT, UPDATE ON audiobook_progress TO tonezen_api;
GRANT SELECT, INSERT, UPDATE ON invite_codes TO tonezen_api;
GRANT SELECT, INSERT ON invite_redemptions TO tonezen_api;

-- Internal storage paths must not be readable via PostgREST as authenticated.
REVOKE SELECT ON track_files FROM authenticated;
DROP POLICY IF EXISTS track_files_select ON track_files;
CREATE POLICY track_files_select ON track_files
  FOR SELECT TO service_role
  USING (true);

-- Indexer state: enable RLS with service_role-only access (grant-only was fragile).
ALTER TABLE indexer_state ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS indexer_state_service_all ON indexer_state;
CREATE POLICY indexer_state_service_all ON indexer_state
  FOR ALL TO service_role
  USING (true)
  WITH CHECK (true);

-- Leftover broad storage grant from 007; avatars remain readable via policy.
REVOKE SELECT ON storage.objects FROM anon;
