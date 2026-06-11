-- TPlayer catalog schema + RLS
-- Migration: 001_initial_schema.sql

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Catalog tables (managed by indexer, read-only for clients)
CREATE TABLE IF NOT EXISTS cycles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    slug TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    description TEXT,
    book_order JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS books (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    slug TEXT NOT NULL UNIQUE,
    content_type TEXT NOT NULL CHECK (content_type IN ('audiobook', 'music')),
    title TEXT NOT NULL,
    author TEXT,
    cover_path TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS cycle_books (
    cycle_id UUID NOT NULL REFERENCES cycles(id) ON DELETE CASCADE,
    book_id UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (cycle_id, book_id)
);

CREATE TABLE IF NOT EXISTS tracks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    book_id UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    sort_order INT NOT NULL DEFAULT 0,
    title TEXT NOT NULL,
    filename TEXT NOT NULL,
    duration_ms INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS track_files (
    track_id UUID PRIMARY KEY REFERENCES tracks(id) ON DELETE CASCADE,
    storage_path TEXT NOT NULL,
    checksum TEXT,
    size_bytes BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- User data
CREATE TABLE IF NOT EXISTS favorites (
    user_id UUID NOT NULL,
    book_id UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, book_id)
);

CREATE TABLE IF NOT EXISTS audiobook_progress (
    user_id UUID NOT NULL,
    book_id UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    track_id UUID NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
    position_ms INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, book_id)
);

CREATE INDEX IF NOT EXISTS idx_books_content_type ON books(content_type) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_books_updated_at ON books(updated_at);
CREATE INDEX IF NOT EXISTS idx_cycles_updated_at ON cycles(updated_at);
CREATE INDEX IF NOT EXISTS idx_tracks_book_id ON tracks(book_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_tracks_book_filename ON tracks(book_id, filename);
CREATE INDEX IF NOT EXISTS idx_audiobook_progress_user ON audiobook_progress(user_id);

-- Grants for PostgREST roles
GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
GRANT SELECT ON cycles, books, cycle_books, tracks, track_files TO anon, authenticated;
GRANT ALL ON favorites, audiobook_progress TO authenticated;
GRANT ALL ON ALL TABLES IN SCHEMA public TO service_role;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO service_role;

-- RLS
ALTER TABLE cycles ENABLE ROW LEVEL SECURITY;
ALTER TABLE books ENABLE ROW LEVEL SECURITY;
ALTER TABLE cycle_books ENABLE ROW LEVEL SECURITY;
ALTER TABLE tracks ENABLE ROW LEVEL SECURITY;
ALTER TABLE track_files ENABLE ROW LEVEL SECURITY;
ALTER TABLE favorites ENABLE ROW LEVEL SECURITY;
ALTER TABLE audiobook_progress ENABLE ROW LEVEL SECURITY;

-- Catalog: readable by authenticated users (and anon for public catalog)
CREATE POLICY cycles_select ON cycles FOR SELECT TO authenticated, anon USING (deleted_at IS NULL);
CREATE POLICY books_select ON books FOR SELECT TO authenticated, anon USING (deleted_at IS NULL);
CREATE POLICY cycle_books_select ON cycle_books FOR SELECT TO authenticated, anon USING (true);
CREATE POLICY tracks_select ON tracks FOR SELECT TO authenticated, anon USING (deleted_at IS NULL);
CREATE POLICY track_files_select ON track_files FOR SELECT TO authenticated, anon USING (true);

-- Favorites: own rows only
CREATE POLICY favorites_select ON favorites FOR SELECT TO authenticated USING (user_id = auth.uid());
CREATE POLICY favorites_insert ON favorites FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());
CREATE POLICY favorites_delete ON favorites FOR DELETE TO authenticated USING (user_id = auth.uid());

-- Audiobook progress: own rows only
CREATE POLICY progress_select ON audiobook_progress FOR SELECT TO authenticated USING (user_id = auth.uid());
CREATE POLICY progress_insert ON audiobook_progress FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());
CREATE POLICY progress_update ON audiobook_progress FOR UPDATE TO authenticated USING (user_id = auth.uid());
CREATE POLICY progress_delete ON audiobook_progress FOR DELETE TO authenticated USING (user_id = auth.uid());

-- auth.uid() shim for non-supabase postgres (PostgREST provides this via JWT)
CREATE OR REPLACE FUNCTION auth.uid() RETURNS UUID AS $$
  SELECT NULLIF(current_setting('request.jwt.claim.sub', true), '')::UUID;
$$ LANGUAGE sql STABLE;
