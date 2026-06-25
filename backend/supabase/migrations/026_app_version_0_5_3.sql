-- Release metadata for Tonezen 0.5.3.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.5.3',
  ARRAY[
    'Исправлено скачивание и воспроизведение музыки на вкладке «Музыка» — тот же механизм, что в книгах',
    'Ошибки загрузки и воспроизведения показываются через всплывающее уведомление'
  ]::text[],
  '2026-06-25T14:34:16.949Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
