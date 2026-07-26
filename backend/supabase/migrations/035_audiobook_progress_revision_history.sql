-- Audiobook progress: server revision (CAS) + short append-only history for ops restore.

ALTER TABLE audiobook_progress
  ADD COLUMN IF NOT EXISTS revision BIGINT NOT NULL DEFAULT 1;

UPDATE audiobook_progress
SET revision = 1
WHERE revision IS NULL OR revision < 1;

CREATE TABLE IF NOT EXISTS audiobook_progress_history (
  id BIGSERIAL PRIMARY KEY,
  user_id UUID NOT NULL,
  book_id UUID NOT NULL REFERENCES books(id) ON DELETE CASCADE,
  track_id UUID NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
  position_ms INT NOT NULL,
  revision BIGINT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audiobook_progress_history_user_book_recorded
  ON audiobook_progress_history (user_id, book_id, recorded_at DESC);

CREATE OR REPLACE FUNCTION audiobook_progress_history_append()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  INSERT INTO audiobook_progress_history (
    user_id, book_id, track_id, position_ms, revision, updated_at
  ) VALUES (
    NEW.user_id, NEW.book_id, NEW.track_id, NEW.position_ms, NEW.revision, NEW.updated_at
  );

  DELETE FROM audiobook_progress_history h
  WHERE h.user_id = NEW.user_id
    AND h.book_id = NEW.book_id
    AND h.id NOT IN (
      SELECT id
      FROM audiobook_progress_history
      WHERE user_id = NEW.user_id AND book_id = NEW.book_id
      ORDER BY recorded_at DESC, id DESC
      LIMIT 50
    );

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_audiobook_progress_history ON audiobook_progress;
CREATE TRIGGER trg_audiobook_progress_history
  AFTER INSERT OR UPDATE ON audiobook_progress
  FOR EACH ROW
  EXECUTE PROCEDURE audiobook_progress_history_append();

INSERT INTO audiobook_progress_history (
  user_id, book_id, track_id, position_ms, revision, updated_at, recorded_at
)
SELECT
  p.user_id,
  p.book_id,
  p.track_id,
  p.position_ms,
  p.revision,
  p.updated_at,
  p.updated_at
FROM audiobook_progress p
WHERE NOT EXISTS (
  SELECT 1
  FROM audiobook_progress_history h
  WHERE h.user_id = p.user_id
    AND h.book_id = p.book_id
    AND h.revision = p.revision
);

ALTER TABLE audiobook_progress_history ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON audiobook_progress_history FROM PUBLIC;
REVOKE ALL ON audiobook_progress_history FROM anon, authenticated;
GRANT SELECT, INSERT, DELETE ON audiobook_progress_history TO tonezen_api;
GRANT USAGE, SELECT ON SEQUENCE audiobook_progress_history_id_seq TO tonezen_api;
GRANT ALL ON audiobook_progress_history TO service_role;

DROP POLICY IF EXISTS audiobook_progress_history_service_all ON audiobook_progress_history;
CREATE POLICY audiobook_progress_history_service_all ON audiobook_progress_history
  FOR ALL TO service_role
  USING (true)
  WITH CHECK (true);
