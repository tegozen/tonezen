-- Release metadata for Tonezen 0.14.0.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.14.0',
  ARRAY[
    'Убран встроенный плеер с экрана книги — управление только в мини-плеере',
    'Прогресс пишется сразу при старте главы, чтобы продолжение цикла брало последнюю книгу',
    'Уточнён диалог подтверждения при старте более ранней книги в цикле'
  ]::text[],
  '2026-07-26T12:07:23.082Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
