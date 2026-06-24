-- Release metadata for Tonezen 0.3.0.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.3.0',
  ARRAY[
    'Добавлена загрузка отдельных треков с экрана книги в Android и Desktop',
    'Ускорена загрузка библиотеки, синхронизация каталога и загрузка файлов в Android',
    'На лендинге появился экран с историей версий приложения',
    'Улучшен экран книги в Android: убраны лишние элементы управления воспроизведением'
  ]::text[],
  '2026-06-24T15:30:00.160Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
