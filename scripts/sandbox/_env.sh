#!/usr/bin/env bash
# Shared helpers for the sandbox scripts. Sourced, not run directly.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
if [[ -f "$ROOT/.env" ]]; then
  # Strip Windows line endings (Notepad saves CRLF), otherwise every value ends with a hidden \r.
  set -a; source <(tr -d '\r' < "$ROOT/.env"); set +a
fi

: "${PAYPAL_CLIENT_ID:?Set PAYPAL_CLIENT_ID in .env}"
: "${PAYPAL_CLIENT_SECRET:?Set PAYPAL_CLIENT_SECRET in .env}"
PAYPAL_BASE_URL="${PAYPAL_BASE_URL:-https://api-m.sandbox.paypal.com}"

command -v jq >/dev/null || { echo "jq is required (https://jqlang.github.io/jq/)"; exit 1; }

# Prints the access token, or the PayPal error and exits non-zero.
paypal_token() {
  local res token
  res="$(curl -sS -X POST "$PAYPAL_BASE_URL/v1/oauth2/token" \
    -u "$PAYPAL_CLIENT_ID:$PAYPAL_CLIENT_SECRET" \
    -d "grant_type=client_credentials")"
  # jq on Windows ends lines with \r; strip it.
  token="$(echo "$res" | jq -r '.access_token // empty' | tr -d '\r')"
  if [[ -z "$token" ]]; then
    echo "PayPal did not return a token. Response:" >&2
    echo "$res" >&2
    return 1
  fi
  echo "$token"
}
