#!/usr/bin/env bash
# SANDBOX ONLY: moves a dispute into the state where the merchant must submit evidence
# (status WAITING_FOR_SELLER_RESPONSE), so provide-evidence can be tested.
# Usage: ./require-seller-evidence.sh PP-D-12345
# If PayPal rejects the body, check "require-evidence" in the Disputes API reference.
source "$(dirname "$0")/_env.sh"
id="${1:?Usage: $0 <dispute_id>}"
curl -sS -X POST "$PAYPAL_BASE_URL/v1/customer/disputes/$id/require-evidence" \
  -H "Authorization: Bearer $(paypal_token)" \
  -H "Content-Type: application/json" \
  -d '{"action": "SELLER_EVIDENCE"}' | jq .
