-- Release metadata for Tonezen 0.5.5.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.5.5',
  ARRAY[
    'Исправлено скачивание музыки — снова через общую очередь, как у книг',
    'Массовая загрузка музыки больше не прерывает скачивание аудиокниг'
  ]::text[],
  '2026-06-26T06:14:38.872Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
