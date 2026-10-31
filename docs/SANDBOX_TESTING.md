# Day 1–3: prove the sandbox dispute lifecycle

Goal: before writing any AI code, see one real sandbox dispute travel
**PayPal → webhook → Kafka → Postgres**. If this works, everything else is building on solid ground.

## 0. One-time setup

1. Log in to <https://developer.paypal.com> → **Apps & Credentials** (Sandbox) → **Create App**.
   Copy the Client ID and Secret into `.env` (copy `.env.example` first).
2. **Testing Tools → Sandbox Accounts**: you get a default *Business* (merchant) and *Personal* (buyer)
   account. Note the buyer's email and password (click the account → "View/Edit account").
3. Install `jq`, Docker, Java 21 and Maven.

## 1. Credentials work

```bash
./scripts/sandbox/get-token.sh          # expect: OK, got a sandbox access token
```

## 2. Make a real sandbox purchase

```bash
./scripts/sandbox/create-order.sh 49.99 "Wireless earbuds"
# open approve_link, log in as the sandbox PERSONAL account, approve
./scripts/sandbox/capture-order.sh <order_id>    # expect status COMPLETED
```

## 3. Open a dispute as the buyer

1. Go to <https://www.sandbox.paypal.com> and log in as the **Personal** sandbox account.
2. Open the payment in Activity → **Report a problem** → *I didn't receive my item* (Item Not Received).
3. Back in the terminal:

```bash
./scripts/sandbox/list-disputes.sh               # the new dispute should appear (can take a minute)
./scripts/sandbox/get-dispute.sh <dispute_id>    # check reason, status, seller_response_due_date
```

If the sandbox UI misbehaves, retry with a fresh order. Write down every quirk you hit:
it is good material for the "Challenges" section of the Devpost story.

## 4. Run the app and receive the dispute

```bash
docker compose up -d
mvn spring-boot:run
```

**Without webhooks (quickest check):**

```bash
curl -X POST localhost:8080/api/disputes/sync
curl localhost:8080/api/disputes
```

**With webhooks:** PayPal must reach your app over public HTTPS.

```bash
cloudflared tunnel --url http://localhost:8080      # or: ngrok http 8080
./scripts/sandbox/register-webhook.sh https://<tunnel-host>/webhooks/paypal
# put the printed id into .env as PAYPAL_WEBHOOK_ID and restart the app
```

Then open a new dispute (step 3) and watch the logs:

```
Accepted CUSTOMER.DISPUTE.CREATED for dispute PP-D-... -> dispute.created
Received dispute.created for dispute PP-D-...
Synced dispute PP-D-... reason=MERCHANDISE_OR_SERVICE_NOT_RECEIVED status=...
```

Wiring-only test with the simulator (no real dispute needed): start the app with
`PAYPAL_SKIP_WEBHOOK_VERIFICATION=true`, then run
`./scripts/sandbox/simulate-dispute-webhook.sh https://<tunnel-host>/webhooks/paypal`.
The webhook is accepted and reaches Kafka; the sync then fails because the sample dispute id
does not exist. That is expected.

## 5. Move the dispute to "seller must respond"

```bash
./scripts/sandbox/require-seller-evidence.sh <dispute_id>
./scripts/sandbox/get-dispute.sh <dispute_id>    # status should become WAITING_FOR_SELLER_RESPONSE
```

That is the state in which `provide-evidence` works, which the submit service will use later.

## Day 1 done when

- [ ] Token script works
- [ ] One captured sandbox order
- [ ] One dispute visible in `list-disputes.sh`
- [ ] The same dispute stored in Postgres via `/api/disputes/sync`
- [ ] A real `CUSTOMER.DISPUTE.CREATED` webhook logged by the app
- [ ] Dispute moved to `WAITING_FOR_SELLER_RESPONSE`

Fallback if sandbox disputes are flaky: record the real API responses you did get into
`src/test/resources/fixtures/` and replay them in demo mode.
