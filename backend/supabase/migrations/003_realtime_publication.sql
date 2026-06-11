-- Realtime postgres_changes for sync (audiobook progress, favorites, catalog)

ALTER TABLE audiobook_progress REPLICA IDENTITY FULL;
ALTER TABLE favorites REPLICA IDENTITY FULL;
ALTER TABLE books REPLICA IDENTITY FULL;
ALTER TABLE cycles REPLICA IDENTITY FULL;
ALTER TABLE tracks REPLICA IDENTITY FULL;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
    CREATE PUBLICATION supabase_realtime;
  END IF;
END $$;

ALTER PUBLICATION supabase_realtime ADD TABLE audiobook_progress;
ALTER PUBLICATION supabase_realtime ADD TABLE favorites;
ALTER PUBLICATION supabase_realtime ADD TABLE books;
ALTER PUBLICATION supabase_realtime ADD TABLE cycles;
ALTER PUBLICATION supabase_realtime ADD TABLE tracks;
