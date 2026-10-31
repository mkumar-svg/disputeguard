#!/usr/bin/env bash
# Captures an approved sandbox order, so the money actually moves and the buyer can dispute it.
# Usage: ./capture-order.sh <order_id>
source "$(dirname "$0")/_env.sh"
id="${1:?Usage: $0 <order_id>}"
curl -sS -X POST "$PAYPAL_BASE_URL/v2/checkout/orders/$id/capture" \
  -H "Authorization: Bearer $(paypal_token)" \
  -H "Content-Type: application/json" |
  jq '{order_id: .id, status, capture_id: .purchase_units[0].payments.captures[0].id, error: .name, details: .details}'
