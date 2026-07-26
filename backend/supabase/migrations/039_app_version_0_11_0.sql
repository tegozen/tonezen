-- Release metadata for Tonezen 0.11.0.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.11.0',
  ARRAY[
    'На Desktop синхронизация каталога удаляет устаревшие книги и треки, которых больше нет на сервере',
    'После переиндексации музыки больше не остаются «призрачные» альбомы с ошибкой скачивания'
  ]::text[],
  '2026-07-26T11:02:50.705Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
