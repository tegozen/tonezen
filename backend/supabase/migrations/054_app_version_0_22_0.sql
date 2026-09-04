-- Release metadata for Tonezen 0.22.0.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.22.0',
  ARRAY[
    'Добавлена проверка поискового запроса при настройке отслеживания новых книг.',
    'Добавлен процент прослушивания книг в цикле и наглядная отметка скачанных книг.'
  ]::text[],
  '2026-09-04T12:01:17.676Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
