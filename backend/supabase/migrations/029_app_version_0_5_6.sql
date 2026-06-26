-- Release metadata for Tonezen 0.5.6.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.5.6',
  ARRAY[
    'Исправлено скачивание музыки — тот же bookId каталога и проверка localPath, что у аудиокниг',
    'Одиночная загрузка снова показывает ошибку; «Скачать все» не сбивает очередь загрузок'
  ]::text[],
  '2026-06-26T07:16:13.045Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
