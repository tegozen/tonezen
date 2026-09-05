-- Release metadata for Tonezen 0.23.0.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.23.0',
  ARRAY[
    'Добавлена типобезопасная навигация с сохранением состояния вкладок и экранов.',
    'Улучшено отображение активных загрузок и прогресса прослушивания книг в циклах.',
    'Повышена стабильность отслеживания новых книг, синхронизации прогресса и воспроизведения на Android.'
  ]::text[],
  '2026-09-05T11:31:17.800Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
