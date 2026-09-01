-- Release metadata for Tonezen 0.21.0.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.21.0',
  ARRAY[
    'Добавлено слежение за новыми аудиокнигами с уведомлениями и историей событий',
    'Исправлено отображение текста с повреждённой кодировкой в каталоге',
    'Отключено переключение глав аудиокниг внешними медиакнопками гарнитуры и системы'
  ]::text[],
  '2026-09-01T22:34:20.641Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
