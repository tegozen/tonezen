-- Release metadata for Tonezen 0.4.0.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.4.0',
  ARRAY[
    'Добавлено: Упрощение интерфейса аудиокниг и улучшение загрузки треков. Исправлено: Показ галочки в меню при полной загрузке аудиокниги.'
  ]::text[],
  '2026-06-25T10:51:59.978Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
