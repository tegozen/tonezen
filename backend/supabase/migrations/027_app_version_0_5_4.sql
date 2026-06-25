-- Release metadata for Tonezen 0.5.4.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.5.4',
  ARRAY[
    'Исправлено скачивание отдельных треков на вкладке «Музыка» — корректное определение альбома и устранение ложного статуса «скачано»',
    'Загрузка музыки использует тот же механизм, что и в разделе книг; ошибки снова показываются во всплывающем уведомлении'
  ]::text[],
  '2026-06-25T14:47:52.084Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
