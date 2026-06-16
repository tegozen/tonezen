#!/bin/sh
set -eu

POSTGRES_DB="${POSTGRES_DB:-tonezen}"
export PGPASSWORD="${PGPASSWORD:-tonezen-postgres-internal}"

wait_for_storage_buckets() {
  for attempt in $(seq 1 60); do
    if psql -h db -U supabase_admin -d "$POSTGRES_DB" -tAc \
      "SELECT 1 FROM information_schema.tables WHERE table_schema = 'storage' AND table_name = 'buckets'" \
      | grep -q 1; then
      return 0
    fi
    echo "Waiting for storage.buckets ($attempt/60)..."
    sleep 2
  done

  echo "storage.buckets not found. storage-api migrations did not run." >&2
  echo "Try: docker compose up -d --force-recreate storage && docker compose logs storage" >&2
  exit 1
}

apply_sql() {
  file="$1"
  echo "Applying $(basename "$file")..."
  psql -h db -U supabase_admin -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -f "$file"
}

wait_for_storage_buckets

for file in /bootstrap/*.sql; do
  apply_sql "$file"
done

echo "Storage DB bootstrap complete."
