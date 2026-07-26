-- Release metadata for Tonezen 0.19.0.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.19.0',
  ARRAY[
    'Добавлена синхронизация прогресса аудиокниг между Android-устройствами по блютус без интернета',
    'На сайте обновлено описание синхронизации прогресса'
  ]::text[],
  '2026-07-26T22:37:36.122Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
