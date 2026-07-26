-- Release metadata for Tonezen 0.7.0.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.7.0',
  ARRAY[
    'Улучшена синхронизация прогресса аудиокниг: после переустановки или входа сначала загружаются данные с сервера, чтобы не стереть прогресс',
    'При холодном старте и смене сессии прогресс синхронизируется до появления интерфейса',
    'Добавлен таймаут загрузки прогресса, чтобы приложение не зависало при проблемах с сетью'
  ]::text[],
  '2026-07-26T06:19:02.906Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
