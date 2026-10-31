#!/usr/bin/env bash
# Lists the merchant's sandbox disputes: id, reason, status, amount.
source "$(dirname "$0")/_env.sh"
out="$(curl -sS "$PAYPAL_BASE_URL/v1/customer/disputes" \
  -H "Authorization: Bearer $(paypal_token)" |
  jq -r '.items // [] | (["DISPUTE_ID","REASON","STATUS","AMOUNT"] | @tsv),
         (.[] | [.dispute_id, .reason, .status, "\(.dispute_amount.value) \(.dispute_amount.currency_code)"] | @tsv)')"
# `column` is missing in Git Bash on Windows, so fall back to tab-separated output.
if command -v column >/dev/null; then echo "$out" | column -t -s $'\t'; else echo "$out"; fi
