#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

VOLUME_NAME="tonezen-storage"

usage() {
  cat <<EOF
Restore Tonezen storage from a tar.gz archive into the Docker volume.

Usage:
  $(basename "$0") <archive.tar.gz>

Examples:
  $(basename "$0") backups/tonezen-storage-20260101-120000.tar.gz

Services that mount storage are stopped for the duration of the import.
Run 'docker compose up -d' afterwards if the stack is not already running.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

ARCHIVE="${1:-}"
if [[ -z "$ARCHIVE" ]]; then
  usage >&2
  exit 1
fi

if [[ ! -f "$ARCHIVE" ]]; then
  echo "Archive not found: $ARCHIVE" >&2
  exit 1
fi

ABS_ARCHIVE="$(cd "$(dirname "$ARCHIVE")" && pwd)/$(basename "$ARCHIVE")"

echo "==> Stopping services that use storage"
docker compose stop storage indexer imgproxy >/dev/null 2>&1 || true

echo "==> Ensuring volume '$VOLUME_NAME' exists"
docker volume create "$VOLUME_NAME" >/dev/null

echo "==> Restoring ${ABS_ARCHIVE} into '$VOLUME_NAME'"
docker run --rm \
  -v "${VOLUME_NAME}:/volume" \
  -v "${ABS_ARCHIVE}:/backup.tar.gz:ro" \
  alpine sh -c 'find /volume -mindepth 1 -maxdepth 1 -exec rm -rf {} +; tar xzf /backup.tar.gz -C /volume'

echo "==> Starting stack"
docker compose up -d storage indexer imgproxy

echo "==> Done. Storage restored from $(basename "$ARCHIVE")"
