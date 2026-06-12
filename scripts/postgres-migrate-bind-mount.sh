#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

LEGACY_DIR="${ROOT}/data/postgres"
VOLUME_NAME="tonezen-postgres"

usage() {
  cat <<EOF
One-time migration: legacy bind mount ./data/postgres -> Docker volume tonezen-postgres.

Safe to re-run: skips when the volume already has Postgres data.

Usage:
  $(basename "$0")
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if ! docker run --rm -v "${LEGACY_DIR}:/source:ro" alpine test -f /source/PG_VERSION 2>/dev/null; then
  echo "No legacy Postgres data at ${LEGACY_DIR} — nothing to migrate."
  exit 0
fi

docker volume create "$VOLUME_NAME" >/dev/null

if docker run --rm -v "${VOLUME_NAME}:/volume:ro" alpine test -f /volume/PG_VERSION; then
  echo "Volume '$VOLUME_NAME' already contains Postgres data — skipping migration."
  exit 0
fi

echo "==> Stopping stack"
docker compose down >/dev/null 2>&1 || true

echo "==> Copying ${LEGACY_DIR} -> volume '$VOLUME_NAME'"
docker run --rm \
  -v "${LEGACY_DIR}:/source:ro" \
  -v "${VOLUME_NAME}:/dest" \
  alpine sh -c 'cp -a /source/. /dest/.'

echo "==> Starting stack"
docker compose up -d

echo "==> Migration complete."
echo "    Legacy directory kept at ${LEGACY_DIR} — remove manually after verifying the stack."
