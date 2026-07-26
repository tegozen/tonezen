-- Release metadata for Tonezen 0.17.0.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.17.0',
  ARRAY[
    'Добавлен сбор крашей и ошибок в self-hosted GlitchTip на Android и Desktop',
    'На лендинге добавлена кнопка перехода к журналу ошибок'
  ]::text[],
  '2026-07-26T21:23:16.430Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
