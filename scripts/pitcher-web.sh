#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if ! command -v cargo >/dev/null 2>&1; then
  echo "cargo is required" >&2
  exit 1
fi

if [ -d web ] && [ ! -d web/dist ]; then
  echo "building web frontend..."
  (cd web && npm install --silent && npm run build)
fi

echo "building server..."
cargo build --release -p pitcher-server

PITCHER_WEB="${PITCHER_WEB:-web/dist}" \
PITCHER_DATA="${PITCHER_DATA:-data}" \
  ./target/release/pitcher-server
