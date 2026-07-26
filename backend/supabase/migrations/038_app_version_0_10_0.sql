-- Release metadata for Tonezen 0.10.0.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.10.0',
  ARRAY[
    'Исправлен выход из аккаунта в профиле на Desktop и Android — сессия больше не возвращается после «Выйти»',
    'Стабильнее обновление токена: незавершённый refresh не перезаписывает выход',
    'На API снят лимит подписи ссылок загрузок — массовое «Скачать всё» не упирается в 429 (нужен деплой API)'
  ]::text[],
  '2026-07-26T10:52:37.908Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
