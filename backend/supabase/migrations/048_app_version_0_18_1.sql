-- Release metadata for Tonezen 0.18.1.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.18.1',
  ARRAY[
    'Исправлена ошибка запуска Android («DSN is required» при инициализации отчётов о сбоях)',
    'GlitchTip работает без Redis/Valkey — очередь через PostgreSQL'
  ]::text[],
  '2026-07-26T21:59:05.271Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
