#!/usr/bin/env bash
# Subscribes a public URL to dispute webhooks and prints the webhook id (put it in .env as PAYPAL_WEBHOOK_ID).
# Usage: ./register-webhook.sh https://<your-tunnel-or-render-host>/webhooks/paypal
source "$(dirname "$0")/_env.sh"
url="${1:?Usage: $0 <public https url ending in /webhooks/paypal>}"
curl -sS -X POST "$PAYPAL_BASE_URL/v1/notifications/webhooks" \
  -H "Authorization: Bearer $(paypal_token)" \
  -H "Content-Type: application/json" \
  -d "$(jq -n --arg url "$url" '{
        url: $url,
        event_types: [
          {name: "CUSTOMER.DISPUTE.CREATED"},
          {name: "CUSTOMER.DISPUTE.UPDATED"},
          {name: "CUSTOMER.DISPUTE.RESOLVED"}
        ]}')" | jq '{id, url, event_types: [.event_types[]?.name], error: .name, message: .message}'
