-- Release metadata for Tonezen 0.18.0.

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.18.0',
  ARRAY[
    'Символы крашей (mapping Android и source maps Desktop) складываются рядом с файлами загрузки для деобфускации в GlitchTip',
    'Сборка релиза больше не загружает символы на сервер сама — их можно залить по FTP вместе с приложениями'
  ]::text[],
  '2026-07-26T21:37:10.976Z'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
