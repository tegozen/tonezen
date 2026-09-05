-- Release metadata for Tonezen 0.26.0.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.26.0',
  ARRAY[
    'Добавлено обновление страницы отслеживания жестом pull-to-refresh.',
    'Исправлено получение результатов проверки новинок без повторного запуска приложения.'
  ]::text[],
  '2026-09-05T20:57:27.470Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
