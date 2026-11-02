#!/usr/bin/env bash
# Approves the evidence pack: DisputeGuard sends it to PayPal. This writes to the sandbox dispute.
# Usage: ./approve.sh <dispute_id> [FIGHT|OFFER_PARTIAL_REFUND|ACCEPT]
# Without an action, the pack's own recommendation is used.
source "$(dirname "$0")/_api.sh"
id="${1:?Usage: $0 <dispute_id> [action]}"
action="${2:-}"
body="$(jq -n --arg a "$action" 'if $a == "" then {approvedBy: "merchant"} else {action: $a, approvedBy: "merchant"} end')"
curl -sS -X POST "$API/api/disputes/$id/approve" -H "Content-Type: application/json" -d "$body" | jq .
