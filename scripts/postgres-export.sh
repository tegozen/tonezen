#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

VOLUME_NAME="tonezen-postgres"
DEFAULT_DIR="backups"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
OUTPUT="${1:-${DEFAULT_DIR}/tonezen-postgres-${TIMESTAMP}.tar.gz}"

usage() {
  cat <<EOF
Export the Tonezen Postgres Docker volume to a tar.gz archive.

Stops the stack briefly so the data directory is consistent.

Usage:
  $(basename "$0") [output.tar.gz]

Examples:
  $(basename "$0")
  $(basename "$0") backups/my-db.tar.gz

Restore with postgres-import.sh on another host.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if ! docker volume inspect "$VOLUME_NAME" >/dev/null 2>&1; then
  echo "Volume '$VOLUME_NAME' not found. Start the stack first: docker compose up -d" >&2
  exit 1
fi

mkdir -p "$(dirname "$OUTPUT")"
ABS_DIR="$(cd "$(dirname "$OUTPUT")" && pwd)"
BACKUP_FILE="$(basename "$OUTPUT")"

echo "==> Stopping stack (Postgres must not write during export)"
docker compose stop >/dev/null

echo "==> Exporting volume '$VOLUME_NAME' to ${ABS_DIR}/${BACKUP_FILE}"
docker run --rm \
  -v "${VOLUME_NAME}:/volume:ro" \
  -v "${ABS_DIR}:/backup" \
  alpine tar czf "/backup/${BACKUP_FILE}" -C /volume .

echo "==> Starting stack"
docker compose start >/dev/null

echo "==> Done: ${ABS_DIR}/${BACKUP_FILE}"
du -h "${ABS_DIR}/${BACKUP_FILE}" 2>/dev/null || ls -lh "${ABS_DIR}/${BACKUP_FILE}"
