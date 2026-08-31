-- Personal audiobook release watches and durable checks.
CREATE TABLE book_watches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    cycle_id UUID NOT NULL REFERENCES cycles(id) ON DELETE CASCADE,
    display_title TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_success_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, cycle_id)
);

CREATE TABLE book_watch_queries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    watch_id UUID NOT NULL REFERENCES book_watches(id) ON DELETE CASCADE,
    provider TEXT NOT NULL CHECK (provider IN ('baza_knig', 'allbookerka')),
    query TEXT NOT NULL CHECK (length(trim(query)) BETWEEN 1 AND 200),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (watch_id, provider, query)
);

CREATE TABLE book_watch_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    watch_id UUID NOT NULL REFERENCES book_watches(id) ON DELETE CASCADE,
    kind TEXT NOT NULL CHECK (kind IN ('book', 'provider_error')),
    dedupe_key TEXT NOT NULL,
    title TEXT NOT NULL,
    author TEXT,
    book_number INT,
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'completed')),
    read_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    occurrence_count INT NOT NULL DEFAULT 1,
    UNIQUE (user_id, watch_id, kind, dedupe_key)
);

CREATE TABLE book_watch_event_links (
    event_id UUID NOT NULL REFERENCES book_watch_events(id) ON DELETE CASCADE,
    provider TEXT NOT NULL CHECK (provider IN ('baza_knig', 'allbookerka')),
    url TEXT NOT NULL,
    PRIMARY KEY (event_id, provider, url)
);

CREATE TABLE book_watch_jobs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    status TEXT NOT NULL DEFAULT 'queued' CHECK (status IN ('queued', 'running', 'completed', 'failed')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    error TEXT
);
CREATE INDEX idx_book_watch_jobs_queue ON book_watch_jobs (created_at) WHERE status = 'queued';
CREATE INDEX idx_book_watch_events_user ON book_watch_events (user_id, first_seen_at DESC);

INSERT INTO book_watches (user_id, cycle_id)
SELECT u.id, c.id FROM auth.users u CROSS JOIN cycles c WHERE c.deleted_at IS NULL
ON CONFLICT DO NOTHING;

INSERT INTO book_watch_queries (watch_id, provider, query)
SELECT w.id, p.provider, c.title
FROM book_watches w JOIN cycles c ON c.id = w.cycle_id
CROSS JOIN (VALUES ('baza_knig'), ('allbookerka')) AS p(provider)
ON CONFLICT DO NOTHING;

ALTER TABLE book_watches ENABLE ROW LEVEL SECURITY;
ALTER TABLE book_watch_queries ENABLE ROW LEVEL SECURITY;
ALTER TABLE book_watch_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE book_watch_event_links ENABLE ROW LEVEL SECURITY;
ALTER TABLE book_watch_jobs ENABLE ROW LEVEL SECURITY;
GRANT ALL ON book_watches, book_watch_queries, book_watch_events,
    book_watch_event_links, book_watch_jobs TO service_role;
