#!/usr/bin/env bash
# Shows full details for one dispute.   Usage: ./get-dispute.sh PP-D-12345
source "$(dirname "$0")/_env.sh"
id="${1:?Usage: $0 <dispute_id>}"
curl -sS "$PAYPAL_BASE_URL/v1/customer/disputes/$id" \
  -H "Authorization: Bearer $(paypal_token)" | jq .
