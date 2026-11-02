#!/usr/bin/env bash
# Step 1 check: are the sandbox credentials right? Prints the start of an access token.
source "$(dirname "$0")/_env.sh"
token="$(paypal_token)" || { echo "Check PAYPAL_CLIENT_ID / PAYPAL_CLIENT_SECRET in .env (Sandbox, not Live)." >&2; exit 1; }
echo "OK, got a sandbox access token: ${token:0:12}... (${#token} characters)"
