#!/usr/bin/env bash
# Shared settings for the demo scripts, which call the running DisputeGuard app. Sourced, not run directly.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
if [[ -f "$ROOT/.env" ]]; then
  set -a; source <(tr -d '\r' < "$ROOT/.env"); set +a
fi
API="${API_URL:-http://localhost:${PORT:-8080}}"
command -v jq >/dev/null || { echo "jq is required"; exit 1; }
