-- Mirror of GoTrue profile fields for Realtime cross-device sync (avatar, display name).

CREATE TABLE IF NOT EXISTS user_profiles (
    user_id UUID PRIMARY KEY,
    display_name TEXT,
    avatar_url TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE user_profiles REPLICA IDENTITY FULL;

GRANT SELECT, INSERT, UPDATE ON user_profiles TO authenticated;

ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'user_profiles' AND policyname = 'user_profiles_select'
  ) THEN
    CREATE POLICY user_profiles_select
      ON user_profiles FOR SELECT TO authenticated
      USING (user_id = auth.uid());
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'user_profiles' AND policyname = 'user_profiles_insert'
  ) THEN
    CREATE POLICY user_profiles_insert
      ON user_profiles FOR INSERT TO authenticated
      WITH CHECK (user_id = auth.uid());
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'user_profiles' AND policyname = 'user_profiles_update'
  ) THEN
    CREATE POLICY user_profiles_update
      ON user_profiles FOR UPDATE TO authenticated
      USING (user_id = auth.uid())
      WITH CHECK (user_id = auth.uid());
  END IF;
END $$;

ALTER PUBLICATION supabase_realtime ADD TABLE user_profiles;
