-- Release metadata for Tonezen 0.19.1.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.19.1',
  ARRAY[
    'Исправлено падение приложения при запуске из-за преждевременного запуска сервиса воспроизведения'
  ]::text[],
  '2026-07-26T22:48:44.263Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
