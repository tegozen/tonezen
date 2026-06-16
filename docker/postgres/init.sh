#!/bin/bash
set -e

# Supabase roles for GoTrue, PostgREST, and storage-api.
# Tonezen SQL migrations run later via supabase/postgres migrate.sh (after init-scripts create storage).
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
  CREATE EXTENSION IF NOT EXISTS "pgcrypto";

  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'anon') THEN
      CREATE ROLE anon NOLOGIN NOINHERIT;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'authenticated') THEN
      CREATE ROLE authenticated NOLOGIN NOINHERIT;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'service_role') THEN
      CREATE ROLE service_role NOLOGIN NOINHERIT BYPASSRLS;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'authenticator') THEN
      CREATE ROLE authenticator NOINHERIT LOGIN PASSWORD '${POSTGRES_PASSWORD}';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'supabase_auth_admin') THEN
      CREATE ROLE supabase_auth_admin NOINHERIT CREATEROLE LOGIN PASSWORD '${POSTGRES_PASSWORD}';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'supabase_admin') THEN
      CREATE ROLE supabase_admin NOINHERIT CREATEROLE LOGIN REPLICATION BYPASSRLS PASSWORD '${POSTGRES_PASSWORD}';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'supabase_storage_admin') THEN
      CREATE ROLE supabase_storage_admin NOINHERIT CREATEROLE LOGIN PASSWORD '${POSTGRES_PASSWORD}';
    END IF;
  END
  \$\$;

  GRANT ALL ON SCHEMA public TO supabase_admin;
  GRANT ALL ON ALL TABLES IN SCHEMA public TO supabase_admin;
  GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO supabase_admin;
  ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO supabase_admin;

  GRANT anon TO authenticator;
  GRANT authenticated TO authenticator;
  GRANT service_role TO authenticator;
  GRANT ALL ON SCHEMA public TO supabase_auth_admin;

  CREATE SCHEMA IF NOT EXISTS auth AUTHORIZATION supabase_auth_admin;
  GRANT USAGE ON SCHEMA auth TO anon, authenticated, service_role, supabase_admin;
  GRANT ALL ON SCHEMA auth TO supabase_auth_admin;

  CREATE SCHEMA IF NOT EXISTS _realtime AUTHORIZATION supabase_admin;
  GRANT ALL ON SCHEMA _realtime TO supabase_admin;

  GRANT ALL ON DATABASE ${POSTGRES_DB} TO supabase_storage_admin;
  GRANT ALL ON DATABASE ${POSTGRES_DB} TO supabase_auth_admin;

  -- storage-api impersonates JWT roles via authenticator (supabase/storage#369)
  GRANT authenticator TO supabase_storage_admin;
  REVOKE anon, authenticated, service_role FROM supabase_storage_admin;
  ALTER ROLE supabase_storage_admin SET search_path TO storage, public;

  ALTER ROLE supabase_auth_admin SET search_path TO auth, public;
EOSQL
