-- Release metadata for Tonezen 0.6.0.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.6.0',
  ARRAY[
    'Добавлено автовозобновление прослушивания книг из библиотеки',
    'Добавлена смена пароля с проверкой текущего пароля на Android',
    'Улучшена загрузка библиотеки на десктопе',
    'Исправлено переключение треков в очереди по типу контента',
    'Исправлена выдача подписанных ссылок на скачивание'
  ]::text[],
  '2026-07-26T05:55:31.487Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
