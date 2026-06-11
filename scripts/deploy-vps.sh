#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ ! -f .env ]]; then
  echo "Copy .env.example to .env and configure secrets before deploying."
  cp .env.example .env
  exit 1
fi

echo "==> Pulling images and starting stack..."
docker compose pull
docker compose up -d --build

echo "==> Waiting for Postgres..."
until docker compose exec -T db pg_isready -U postgres >/dev/null 2>&1; do
  sleep 2
done

echo "==> Running indexer once..."
docker compose run --rm indexer node dist/index.js || true

echo "==> Health checks"
curl -sf "http://localhost:3001/health" >/dev/null && echo "API ok"
curl -sf "http://localhost:8080/health" >/dev/null && echo "Nginx ok"

echo "Deploy complete."
echo "  Kong/API gateway: http://localhost:8000"
echo "  FTP: port 21 (passive 21100-21110)"
echo "  Mail catcher (dev): http://localhost:9000"
