#!/bin/sh
set -eu

MIGRATIONS_DIR="${MIGRATIONS_DIR:-/migrations}"
PGHOST="${PGHOST:-db}"
PGUSER="${PGUSER:-supabase_admin}"
PGPASSWORD="${PGPASSWORD:-tonezen-postgres-internal}"
PGDATABASE="${PGDATABASE:-tonezen}"
export PGPASSWORD

until pg_isready -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" >/dev/null 2>&1; do
  echo "[migrate] waiting for postgres..."
  sleep 1
done

psql -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -v ON_ERROR_STOP=1 <<'SQL'
CREATE TABLE IF NOT EXISTS schema_migrations (
  filename TEXT PRIMARY KEY,
  applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
SQL

migration_count=$(psql -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -tAc \
  "SELECT COUNT(*) FROM schema_migrations")
books_exists=$(psql -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -tAc \
  "SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'books' LIMIT 1")
legacy_db=false
if [ "$migration_count" = "0" ] && [ "$books_exists" = "1" ]; then
  legacy_db=true
fi

migration_number() {
  basename "$1" | sed -n 's/^\([0-9][0-9]*\).*/\1/p'
}

waveform_peaks_exists() {
  [ "$(psql -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -tAc \
    "SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'track_files' AND column_name = 'waveform_peaks' LIMIT 1")" = "1" ]
}

record_migration() {
  filename=$1
  psql -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -v ON_ERROR_STOP=1 \
    -c "INSERT INTO schema_migrations (filename) VALUES ('${filename}') ON CONFLICT DO NOTHING"
}

is_recorded() {
  filename=$1
  [ "$(psql -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -tAc \
    "SELECT 1 FROM schema_migrations WHERE filename = '${filename}' LIMIT 1")" = "1" ]
}

for file in $(ls "$MIGRATIONS_DIR"/*.sql 2>/dev/null | sort); do
  filename=$(basename "$file")
  if is_recorded "$filename"; then
    echo "[migrate] skip ${filename}"
    continue
  fi

  if [ "$legacy_db" = true ]; then
    num=$(migration_number "$file")
    if [ -n "$num" ] && [ "$num" -lt 15 ]; then
      echo "[migrate] legacy mark ${filename}"
      record_migration "$filename"
      continue
    fi
    if [ "$filename" = "015_track_waveform_peaks.sql" ] && waveform_peaks_exists; then
      echo "[migrate] legacy mark ${filename}"
      record_migration "$filename"
      continue
    fi
  fi

  echo "[migrate] apply ${filename}"
  psql -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -v ON_ERROR_STOP=1 -f "$file"
  record_migration "$filename"
done

echo "[migrate] done"
