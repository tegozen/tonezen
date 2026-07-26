#!/bin/sh
set -eu

MIGRATIONS_DIR="${MIGRATIONS_DIR:-/migrations}"
PGHOST="${PGHOST:-db}"
PGUSER="${PGUSER:-supabase_admin}"
# Compose injects POSTGRES_PASSWORD here. Migration 005 temporarily sets roles to
# a hardcoded placeholder; if migrate dies mid-run, reconnect must fall back.
TARGET_PASSWORD="${PGPASSWORD:?Set PGPASSWORD / POSTGRES_PASSWORD in .env}"
PLACEHOLDER_PASSWORD="tonezen-postgres-internal"
PGDATABASE="${PGDATABASE:-tonezen}"
PGPASSWORD="$TARGET_PASSWORD"
export PGPASSWORD

until pg_isready -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" >/dev/null 2>&1; do
  echo "[migrate] waiting for postgres..."
  sleep 1
done

can_connect() {
  psql -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -c "SELECT 1" >/dev/null 2>&1
}

sync_role_passwords() {
  pwd=$1
  echo "[migrate] syncing role passwords"
  PGPASSWORD="$PGPASSWORD" psql -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -v ON_ERROR_STOP=1 -v pwd="$pwd" <<'SQL'
ALTER ROLE supabase_admin WITH PASSWORD :'pwd';
ALTER ROLE supabase_auth_admin WITH PASSWORD :'pwd';
ALTER ROLE supabase_storage_admin WITH PASSWORD :'pwd';
ALTER ROLE authenticator WITH PASSWORD :'pwd';
SQL
  PGPASSWORD="$PGPASSWORD" psql -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -v ON_ERROR_STOP=1 -v pwd="$pwd" \
    -c "ALTER ROLE tonezen_api WITH PASSWORD :'pwd'" 2>/dev/null || true
  PGPASSWORD="$pwd"
  export PGPASSWORD
}

if ! can_connect; then
  echo "[migrate] POSTGRES_PASSWORD rejected; trying migration-005 placeholder"
  PGPASSWORD="$PLACEHOLDER_PASSWORD"
  export PGPASSWORD
  if ! can_connect; then
    echo "[migrate] cannot authenticate as ${PGUSER} with POSTGRES_PASSWORD or placeholder" >&2
    exit 2
  fi
fi

psql -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -v ON_ERROR_STOP=1 <<'SQL'
CREATE TABLE IF NOT EXISTS schema_migrations (
  filename TEXT PRIMARY KEY,
  applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
SQL

# Heal stuck deploys: DB may still have placeholder while .env has the real secret.
sync_role_passwords "$TARGET_PASSWORD"

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
  # Validate filename before recording (defense against path/SQL injection).
  case "$filename" in
    *[!A-Za-z0-9._-]* | "" )
      echo "[migrate] refusing unsafe migration filename: $filename" >&2
      exit 1
      ;;
  esac
  psql -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -v ON_ERROR_STOP=1 \
    -v filename="$filename" \
    -c "INSERT INTO schema_migrations (filename) VALUES (:'filename') ON CONFLICT DO NOTHING"
}

is_recorded() {
  filename=$1
  case "$filename" in
    *[!A-Za-z0-9._-]* | "" ) return 1 ;;
  esac
  [ "$(psql -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -v filename="$filename" -tAc \
    "SELECT 1 FROM schema_migrations WHERE filename = :'filename' LIMIT 1")" = "1" ]
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

  # 005 resets role passwords to PLACEHOLDER_PASSWORD; restore .env secret before next file.
  if [ "$filename" = "005_storage_admin_grants.sql" ]; then
    PGPASSWORD="$PLACEHOLDER_PASSWORD"
    export PGPASSWORD
    sync_role_passwords "$TARGET_PASSWORD"
  fi
done

sync_role_passwords "$TARGET_PASSWORD"

echo "[migrate] done"
