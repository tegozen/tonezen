-- Release metadata for Tonezen 0.5.7.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.5.7',
  ARRAY[
    'Исправлено скачивание музыки: очередь больше не сбрасывается из-за устаревшего bookId после синхронизации каталога',
    'Загрузки треков ставятся в очередь с каноническим bookId из каталога, как у аудиокниг'
  ]::text[],
  '2026-06-26T07:37:32.411Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
