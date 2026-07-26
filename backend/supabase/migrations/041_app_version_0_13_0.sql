-- Release metadata for Tonezen 0.13.0.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.13.0',
  ARRAY[
    'Добавлен диалог подтверждения при старте более ранней книги в цикле',
    'Продолжение цикла берёт книгу с последним прослушиванием',
    'Отметка «не прослушанным» сбрасывает прогресс и синхронизирует его с сервером',
    'Прогресс аудиокниги сохраняется при уходе приложения в фон после паузы'
  ]::text[],
  '2026-07-26T11:46:27.333Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
