#!/usr/bin/env bash
# Creates a sandbox order and prints the link where the sandbox BUYER approves the payment.
# Usage: ./create-order.sh [amount] [description]
source "$(dirname "$0")/_env.sh"
amount="${1:-49.99}"
desc="${2:-DisputeGuard demo item}"
curl -sS -X POST "$PAYPAL_BASE_URL/v2/checkout/orders" \
  -H "Authorization: Bearer $(paypal_token)" \
  -H "Content-Type: application/json" \
  -d "$(jq -n --arg v "$amount" --arg d "$desc" '{
        intent: "CAPTURE",
        purchase_units: [{amount: {currency_code: "USD", value: $v}, description: $d}]}')" |
  jq '{order_id: .id, status, approve_link: ([.links[]? | select(.rel == "approve" or .rel == "payer-action") | .href][0])}'
echo "Open approve_link, log in as your sandbox PERSONAL account, approve, then run ./capture-order.sh <order_id>"
