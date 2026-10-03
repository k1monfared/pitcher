#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PREFERRED_PORT="${PITCHER_PORT:-7373}"
PORT_SCAN="${PITCHER_PORT_SCAN:-200}"
DATA_DIR="${PITCHER_DATA:-$ROOT/data}"
WEB_DIST="$ROOT/web/dist"

log() { printf '\033[36m[pitcher]\033[0m %s\n' "$*"; }
err() { printf '\033[31m[pitcher]\033[0m %s\n' "$*" >&2; }

need() {
  if ! command -v "$1" >/dev/null 2>&1; then
    err "missing required command: $1"
    err "$2"
    exit 1
  fi
}

need cargo "Install Rust from https://rustup.rs"
need ffmpeg "Install ffmpeg (with the rubberband filter) via your package manager"

if ! command -v node >/dev/null 2>&1; then
  err "missing required command: node"
  err "Install Node.js (nvm, or your package manager) to build the web UI."
  exit 1
fi

if [ ! -d "$ROOT/web/node_modules" ]; then
  log "installing web dependencies (first run only)..."
  (cd web && npm install)
fi

if [ "${PITCHER_REBUILD_WEB:-0}" = "1" ] || [ ! -f "$WEB_DIST/index.html" ]; then
  log "building web frontend..."
  (cd web && npm run build)
fi

log "building server..."
cargo build --release -p pitcher-server

find_free_port() {
  local start="$1" scan="$2" p
  for ((p = start; p < start + scan; p++)); do
    if ! (exec 3<>"/dev/tcp/127.0.0.1/$p") 2>/dev/null; then
      printf '%s' "$p"
      return 0
    fi
    exec 3>&- 2>/dev/null || true
  done
  return 1
}

PORT="$(find_free_port "$PREFERRED_PORT" "$PORT_SCAN")" || {
  err "no free port in ${PREFERRED_PORT}..$((PREFERRED_PORT + PORT_SCAN))"
  exit 1
}

if [ "$PORT" != "$PREFERRED_PORT" ]; then
  log "port $PREFERRED_PORT was busy, using $PORT"
fi

LAN_IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
log "starting server..."
log "local:  http://localhost:$PORT"
if [ -n "${LAN_IP:-}" ]; then
  log "LAN:    http://$LAN_IP:$PORT"
fi

if [ "${PITCHER_NO_OPEN:-0}" != "1" ] && command -v xdg-open >/dev/null 2>&1; then
  ( sleep 1.5 && xdg-open "http://localhost:$PORT" >/dev/null 2>&1 ) &
fi

PITCHER_WEB="$WEB_DIST" \
PITCHER_DATA="$DATA_DIR" \
PITCHER_PORT="$PORT" \
PITCHER_BIND="$PORT" \
  exec "$ROOT/target/release/pitcher-server"
