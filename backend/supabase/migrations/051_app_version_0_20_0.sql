-- Release metadata for Tonezen 0.20.0.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.20.0',
  ARRAY[
    'Улучшена синхронизация прогресса аудиокниг по Bluetooth: перед передачей сохраняется текущая позиция воспроизведения и корректнее учитываются главы без локального прогресса',
    'Исправлена облачная синхронизация прогресса: локальное прослушивание дальше сервера больше не зависает без отправки на сервер'
  ]::text[],
  '2026-07-26T23:40:52.672Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
