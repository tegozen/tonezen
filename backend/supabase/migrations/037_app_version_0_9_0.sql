-- Release metadata for Tonezen 0.9.0.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.9.0',
  ARRAY[
    'Исправлено скачивание музыки и аудиокниг: API больше не отклоняет токены без claim aud',
    'Ускорен старт «Скачать всё» на Android',
    'В журнал ошибок Desktop пишется причина сбоя загрузки',
    'Оптимизированы запросы каталога при проверке локальных файлов'
  ]::text[],
  '2026-07-26T10:31:11.903Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
