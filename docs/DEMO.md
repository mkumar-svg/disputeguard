# End-to-end demo: purchase → dispute → AI evidence pack → submit

Assumes the app runs locally (see README) with sandbox credentials in `.env`, and that you have
US sandbox Business and Personal accounts (see SANDBOX_TESTING.md).

## A. Use an order you already paid with the scripts

If you created and captured an order with `scripts/sandbox/create-order.sh` + `capture-order.sh`
and opened a dispute on it, import that order so DisputeGuard knows its "fulfilment":

```bash
./scripts/demo/import-order.sh <paypal_order_id> DELIVERED "Wireless earbuds"
curl -s -X POST localhost:8081/api/disputes/sync > /dev/null   # pull disputes, link them to orders
./scripts/demo/analyze.sh <dispute_id>
```

`analyze.sh` prints the checklist, base and final win chance, recommendation, the agent's tool
calls, guardrail findings and the drafted note.

Try the other stories on new orders: `IN_TRANSIT` should lead to a partial-refund offer and
`NO_TRACKING` to accepting the claim.

## B. Full flow through the demo store API

```bash
# 1. Create an order (pick the story the store will "fulfil")
curl -s -X POST localhost:8081/api/store/orders -H "Content-Type: application/json" \
  -d '{"amount": 49.99, "description": "Wireless earbuds", "scenario": "DELIVERED"}' | jq

# 2. Open approveLink in an incognito window, log in as the US Personal sandbox account, Pay Now.
#    The browser lands on example.com: that means approved.

# 3. Capture: money moves, shipment and chat are recorded
curl -s -X POST localhost:8081/api/store/orders/<paypalOrderId>/capture | jq

# 4. As the buyer on sandbox.paypal.com: Activity → the payment → Report a problem →
#    "I didn't receive my item". Wait 2-10 minutes, then:
curl -s -X POST localhost:8081/api/disputes/sync | jq
#    (With a registered webhook this happens on its own, and the pack is built automatically.)

# 5. Build the evidence pack with the AI agent
./scripts/demo/analyze.sh <dispute_id>

# 6. Approve: DisputeGuard sends it to PayPal (writes to the sandbox dispute)
./scripts/demo/approve.sh <dispute_id>              # uses the recommendation
./scripts/demo/approve.sh <dispute_id> ACCEPT       # or choose: FIGHT | OFFER_PARTIAL_REFUND | ACCEPT
```

If PayPal rejects the submission, `approve.sh` prints PayPal's error body and the pack is marked
`SUBMIT_FAILED`; fix the cause and approve again. `availableActions` in `analyze.sh` shows which
actions PayPal allows on the dispute right now.

## Without an LLM key

Leave `LLM_API_KEY` empty and the same flow works with a rule-based pack (model `rules`), which is
useful for testing and as a fallback if the LLM API is down.
