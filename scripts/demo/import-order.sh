#!/usr/bin/env bash
# Brings an order you paid with the sandbox scripts into the demo store and links its dispute.
# Usage: ./import-order.sh <paypal_order_id> [DELIVERED|IN_TRANSIT|NO_TRACKING] [description]
source "$(dirname "$0")/_api.sh"
id="${1:?Usage: $0 <paypal_order_id> [scenario] [description]}"
scenario="${2:-DELIVERED}"
desc="${3:-Wireless earbuds}"
curl -sS -X POST "$API/api/store/orders/import" -H "Content-Type: application/json" \
  -d "$(jq -n --arg id "$id" --arg s "$scenario" --arg d "$desc" '{paypalOrderId: $id, scenario: $s, description: $d}')" |
  jq '{id, paypalOrderId, captureId, amount, scenario, shipment: (.shipment | if . then {carrier, trackingNo, deliveredAt} else null end), messages: (.messages | length), error, details}'
