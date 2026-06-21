-- Original display names for content paths rewritten to ASCII storage keys.

CREATE TABLE IF NOT EXISTS content_display_names (
    storage_path TEXT PRIMARY KEY,
    display_path TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_content_display_names_updated_at
ON content_display_names(updated_at);

ALTER TABLE content_display_names ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS content_display_names_service_role_all ON content_display_names;
CREATE POLICY content_display_names_service_role_all
ON content_display_names
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON content_display_names TO service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON content_display_names TO supabase_admin;
