-- Release metadata for Tonezen 0.12.0.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.12.0',
  ARRAY[
    'Ускорена синхронизация каталога: треки загружаются одним запросом',
    'Улучшена стабильность приложения при просмотре библиотеки во время воспроизведения',
    'Улучшена работа загрузок музыки и экрана книги',
    'Добавлена поддержка перемотки локальных аудиофайлов на компьютере'
  ]::text[],
  '2026-07-26T11:28:34.654Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
