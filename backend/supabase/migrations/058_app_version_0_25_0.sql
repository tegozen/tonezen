-- Release metadata for Tonezen 0.25.0.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.25.0',
  ARRAY[
    'Исправлено сохранение настроек отслеживания новинок и обновление авторизации перед сохранением.',
    'Исправлены серверные права для настроек отслеживания и запуска проверки новых книг.',
    'Исправлено создание поисковых запросов по умолчанию: сохранённые изменения больше не перезаписываются.',
    'Уточнены сообщения об ошибках сохранения настроек отслеживания.'
  ]::text[],
  '2026-09-05T13:02:47.086Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
