#!/usr/bin/env bash
# Asks PayPal to send a sample CUSTOMER.DISPUTE.CREATED webhook to your URL.
# Note: simulated events carry a sample dispute id and cannot be signature-verified, so run the app with
# PAYPAL_SKIP_WEBHOOK_VERIFICATION=true for this test. Fetching the sample dispute's details will fail;
# that is expected. Use a real sandbox dispute (docs/SANDBOX_TESTING.md) for the full flow.
# Usage: ./simulate-dispute-webhook.sh https://<your-tunnel-host>/webhooks/paypal
source "$(dirname "$0")/_env.sh"
url="${1:?Usage: $0 <public https url ending in /webhooks/paypal>}"
curl -sS -X POST "$PAYPAL_BASE_URL/v1/notifications/simulate-event" \
  -H "Authorization: Bearer $(paypal_token)" \
  -H "Content-Type: application/json" \
  -d "$(jq -n --arg url "$url" '{url: $url, event_type: "CUSTOMER.DISPUTE.CREATED", resource_version: "1.0"}')" |
  jq '{id, event_type, dispute: .resource.dispute_id}'
