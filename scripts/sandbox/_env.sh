#!/usr/bin/env bash
# Shared helpers for the sandbox scripts. Sourced, not run directly.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
if [[ -f "$ROOT/.env" ]]; then
  set -a; source "$ROOT/.env"; set +a
fi

: "${PAYPAL_CLIENT_ID:?Set PAYPAL_CLIENT_ID in .env}"
: "${PAYPAL_CLIENT_SECRET:?Set PAYPAL_CLIENT_SECRET in .env}"
PAYPAL_BASE_URL="${PAYPAL_BASE_URL:-https://api-m.sandbox.paypal.com}"

command -v jq >/dev/null || { echo "jq is required (https://jqlang.github.io/jq/)"; exit 1; }

paypal_token() {
  curl -sS -X POST "$PAYPAL_BASE_URL/v1/oauth2/token" \
    -u "$PAYPAL_CLIENT_ID:$PAYPAL_CLIENT_SECRET" \
    -d "grant_type=client_credentials" | jq -r '.access_token'
}
