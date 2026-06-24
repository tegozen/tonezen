-- Public app release history for the landing page.

CREATE TABLE IF NOT EXISTS app_versions (
    version TEXT PRIMARY KEY CHECK (version ~ '^[0-9]+\.[0-9]+\.[0-9]+$'),
    changelog_ru TEXT[] NOT NULL CHECK (array_length(changelog_ru, 1) > 0),
    released_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE app_versions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS app_versions_select ON app_versions;
CREATE POLICY app_versions_select
ON app_versions
FOR SELECT
TO anon, authenticated
USING (true);

DROP POLICY IF EXISTS app_versions_service_admin_all ON app_versions;
CREATE POLICY app_versions_service_admin_all
ON app_versions
FOR ALL
TO service_role, supabase_admin
USING (true)
WITH CHECK (true);

GRANT SELECT ON app_versions TO anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON app_versions TO service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON app_versions TO supabase_admin;

INSERT INTO app_versions (version, changelog_ru, released_at)
VALUES (
  '0.2.0',
  ARRAY[
    'Добавлены брендированные установщики Windows и macOS с иконками Tonezen',
    'Добавлена корректная работа одного экземпляра приложения и фокус уже открытого окна',
    'Исправлено сохранение прогресса аудиокниги при завершении главы',
    'Исправлены загрузки аудиокниг: ошибки видны пользователю, очередь восстанавливается, скачивается только следующий трек',
    'Улучшен экран глав: активная глава прокручивается в видимую область на Android и Desktop',
    'Исправлены realtime-подписки каталога и порядок треков при инкрементальном сканировании',
    'Добавлена поддержка русских названий файлов в Storage через безопасную транслитерацию путей'
  ]::text[],
  '2026-06-23T16:18:41+04:00'::timestamptz
)
ON CONFLICT (version) DO UPDATE
SET changelog_ru = EXCLUDED.changelog_ru,
    released_at = EXCLUDED.released_at;
