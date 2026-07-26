-- Release metadata for Tonezen 0.15.0.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.15.0',
  ARRAY[
    'Исправлена ложная модалка «устройство / облако» после прослушивания на том же устройстве',
    'Конфликт синка прогресса показывается только при реальном расхождении с другим устройством'
  ]::text[],
  '2026-07-26T12:24:25.373Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
