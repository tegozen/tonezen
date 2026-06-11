#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "==> Backend indexer tests"
(cd backend/indexer && npm test)

echo "==> Backend API tests"
(cd backend/api && npm test)

echo "==> Desktop tests"
(cd apps/desktop && npm test)

echo "==> Android unit tests"
(cd apps/android && ./gradlew.bat testDebugUnitTest --no-daemon)

echo "All smoke tests passed."
