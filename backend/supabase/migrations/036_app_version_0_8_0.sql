-- Release metadata for Tonezen 0.8.0.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.8.0',
  ARRAY[
    'Улучшена синхронизация прогресса аудиокниг между устройствами с учётом истории правок',
    'При конфликте локального и серверного прогресса можно выбрать, какую позицию оставить',
    'Прогресс аудиокниг теперь хранится отдельно для каждого пользователя на устройстве'
  ]::text[],
  '2026-07-26T09:50:50.163Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
