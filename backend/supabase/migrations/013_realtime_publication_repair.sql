-- Repair supabase_realtime publication on existing DB volumes (init scripts run only once).

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
    CREATE PUBLICATION supabase_realtime;
  END IF;
END
$$;

ALTER TABLE audiobook_progress REPLICA IDENTITY FULL;
ALTER TABLE books REPLICA IDENTITY FULL;
ALTER TABLE cycles REPLICA IDENTITY FULL;
ALTER TABLE tracks REPLICA IDENTITY FULL;

DO $$
BEGIN
  IF to_regclass('public.user_profiles') IS NOT NULL THEN
    ALTER TABLE user_profiles REPLICA IDENTITY FULL;
  END IF;
END
$$;

DO $$
DECLARE
  table_name text;
BEGIN
  FOREACH table_name IN ARRAY ARRAY[
    'audiobook_progress',
    'books',
    'cycles',
    'tracks',
    'user_profiles'
  ] LOOP
    IF to_regclass('public.' || table_name) IS NULL THEN
      CONTINUE;
    END IF;

    IF NOT EXISTS (
      SELECT 1 FROM pg_publication_tables
      WHERE pubname = 'supabase_realtime' AND tablename = table_name
    ) THEN
      EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE %I', table_name);
    END IF;
  END LOOP;
END
$$;
