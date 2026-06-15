-- Per-track artist (music library has mixed artists under one book)

ALTER TABLE tracks ADD COLUMN IF NOT EXISTS artist TEXT;
