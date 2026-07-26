-- Release metadata for Tonezen 0.16.1.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.16.1',
  ARRAY[
    'Исправлено: компьютер больше не показывает старый прогресс аудиокниги после прослушивания на телефоне',
    'Desktop при каждом запуске и открытии окна снова подтягивает прогресс с сервера'
  ]::text[],
  '2026-07-26T20:22:20.677Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
