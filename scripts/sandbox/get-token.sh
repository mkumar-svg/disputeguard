#!/usr/bin/env bash
# Step 1 check: are the sandbox credentials right? Prints an access token.
source "$(dirname "$0")/_env.sh"
token="$(paypal_token)"
if [[ -z "$token" || "$token" == "null" ]]; then
  echo "Could not get a token. Check PAYPAL_CLIENT_ID / PAYPAL_CLIENT_SECRET in .env." >&2
  exit 1
fi
echo "OK, got a sandbox access token: ${token:0:20}..."
