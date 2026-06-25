-- Release metadata for Tonezen 0.5.2.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.5.2',
  ARRAY[
    'Исправлено «Скачать все» в музыке: загрузка идёт по порядку, как в книгах, без перескакивания между треками',
    'Улучшен показ процентов и статуса скачивания на вкладке музыки'
  ]::text[],
  '2026-06-25T14:12:56.084Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
