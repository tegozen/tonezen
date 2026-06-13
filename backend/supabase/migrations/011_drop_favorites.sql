-- Remove unused favorites table and Realtime publication entry
ALTER PUBLICATION supabase_realtime DROP TABLE favorites;

DROP TABLE IF EXISTS favorites;
