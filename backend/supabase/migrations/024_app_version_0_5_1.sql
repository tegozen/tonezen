-- Release metadata for Tonezen 0.5.1.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.5.1',
  ARRAY[
    'Исправлено массовое скачивание музыки: воспроизведение больше не перебивает очередь загрузок',
    'На вкладке музыки отображается только прогресс загрузки треков, без аудиокниг'
  ]::text[],
  '2026-06-25T13:40:27.334Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
