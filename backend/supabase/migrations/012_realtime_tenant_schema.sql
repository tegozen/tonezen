-- Tenant-side Realtime schema and postgres role (required by realtime container migrations)

CREATE SCHEMA IF NOT EXISTS realtime AUTHORIZATION supabase_admin;
GRANT ALL ON SCHEMA realtime TO supabase_admin;

DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'postgres') THEN
    CREATE ROLE postgres NOLOGIN;
  END IF;
END
$$;
