-- GoTrue auth schema grants for self-hosted Supabase
CREATE SCHEMA IF NOT EXISTS auth AUTHORIZATION supabase_auth_admin;
GRANT USAGE ON SCHEMA auth TO postgres, anon, authenticated, service_role;
GRANT ALL ON SCHEMA auth TO supabase_auth_admin;
