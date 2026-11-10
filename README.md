# DisputeGuard

**An AI agent for PayPal merchant disputes.** When a buyer opens a dispute, DisputeGuard gathers the
evidence, scores the chance of winning, drafts the response, and submits it through the PayPal
Disputes API once the merchant approves.

Built for the [PayPal AI Hackathon](https://paypalaihackathon.devpost.com/).

> Status: MVP. Webhook intake, Kafka pipeline, dispute sync, demo store, AI agent, evidence pack,
> approve-and-submit, and the merchant dashboard all work against the PayPal Sandbox.

## How it works

```
PayPal Sandbox ──webhook──▶ Webhook receiver ──▶ Kafka (dispute.created / dispute.updated)
                            verify signature              │
                            dedupe in Redis               ▼
                                                Enrichment worker ── sync dispute, link its order
                                                          │
                                                          ▼
                                   Evidence checklist (rules) ──▶ AI agent (tool calls)
                                   base win chance                summary, ±0.15 adjustment, note
                                                          │
                                                          ▼
Merchant reviews & approves ──▶ Submit service ──▶ PayPal provide-evidence / make-offer / accept-claim
```

The AI never writes to PayPal on its own: every `provide-evidence`, `accept-claim` or `make-offer`
call needs the merchant's approval.

## How we use PayPal

| PayPal API | Used for |
| --- | --- |
| OAuth 2.0 (`/v1/oauth2/token`) | Access tokens, cached in Redis until shortly before expiry |
| Webhooks (`CUSTOMER.DISPUTE.CREATED / UPDATED / RESOLVED`) | Trigger the pipeline |
| Webhooks (`/v1/notifications/verify-webhook-signature`) | Reject forged deliveries |
| Orders API v2 (`/v2/checkout/orders`, `capture`, `GET`) | Real sandbox purchases in the demo store |
| Disputes API (`/v1/customer/disputes`, `/{id}`) | Dispute details: reason, amount, status, deadline, allowed actions |
| Disputes API (`provide-evidence`) | Submit proof of fulfilment (carrier + tracking) and the note |
| Disputes API (`make-offer`, `accept-claim`) | Settle with a partial refund, or concede a weak case |

Everything runs against the PayPal **Sandbox**. Write actions are offered only when the dispute's
HATEOAS links say PayPal allows them.

## How we use AI

1. **Checklist (rules).** For "item not received", five pieces of evidence PayPal looks for: order
   record, tracking, delivery scan, delivery before the dispute, and seller-buyer communication.
   Each is PRESENT, WEAK or MISSING; the weighted result is an explainable base win chance.
2. **Agent (LLM with tools).** The agent calls `get_order`, `get_tracking` and `get_chat_logs`
   (scoped to this dispute's order), then returns JSON: a summary of the claim, a score adjustment
   capped at ±0.15 with its reason, an optional refund suggestion, and the note for PayPal.
3. **Guardrails.** JSON is parsed and validated; the adjustment is capped; any tracking-style number
   in the note that does not appear in the data is flagged; the note is cut to PayPal's 2000-character
   limit; and nothing is sent without approval.
4. **Recommendation.** Final win chance ≥ 65%: fight. 40–65%: offer a partial refund. Below 40%: accept.

The LLM is any OpenAI-compatible chat-completions endpoint with tool calling, set in `.env`
(Claude via the Anthropic API, or Gemini). Without an API key the pack is built from rules only.

## Demo store

The demo store takes real sandbox payments but ships nothing, so after capture it records a
simulated shipment and chat for one of three stories: `DELIVERED` (strong case), `IN_TRANSIT`
(mixed) or `NO_TRACKING` (weak). This shows how the recommendation changes with the evidence.

## Tech stack

Java 21 · Spring Boot 3 · Apache Kafka · Redis · PostgreSQL (Flyway) · Docker Compose ·
OpenAI-compatible LLM API (Claude / Gemini) · React + Vite (dashboard) · Render (deployment)

## Run locally

Requirements: Java 21, Maven, Docker, `jq`, and a PayPal developer account.

```bash
cp .env.example .env                 # sandbox Client ID/Secret, optional LLM key
docker compose up -d                 # Postgres, Redis, Kafka
set -a; source .env; set +a
mvn spring-boot:run                  # app on http://localhost:8081 (PORT in .env)
```

Check it is up: `curl localhost:8081/actuator/health`. On Windows/Git Bash, `./scripts/start.sh` does all
of the above in one go.

### Dashboard

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173, API calls go to the app on :8081
```

`npm run build` puts the dashboard into the Spring Boot app, which then serves it at
http://localhost:8081. The Docker image builds it automatically.

The dashboard shows the dispute inbox; for each case the win-chance scale (rules score vs. final
score), the evidence checklist, the agent's tool calls and guardrail checks, the order timeline
(payment, tracking scans, buyer messages), and an editable note with a two-step Approve. A Demo store
tab places sandbox orders for each evidence story.

End-to-end walkthrough (sandbox purchase → dispute → AI pack → submit): [docs/DEMO.md](docs/DEMO.md).
Sandbox setup and troubleshooting: [docs/SANDBOX_TESTING.md](docs/SANDBOX_TESTING.md).

## API

| Method | Path | What it does |
| --- | --- | --- |
| `POST` | `/webhooks/paypal` | PayPal webhook receiver |
| `GET` | `/api/disputes` | Inbox: disputes with recommendation and win chance |
| `GET` | `/api/disputes/{id}` | One dispute with order, shipment, chat, evidence pack, allowed actions |
| `POST` | `/api/disputes/sync` | Pull all disputes from PayPal (works without webhooks) |
| `POST` | `/api/disputes/{id}/sync` | Re-sync one dispute |
| `POST` | `/api/disputes/{id}/analyze` | Build or rebuild the evidence pack with the AI agent |
| `POST` | `/api/disputes/{id}/approve` | Merchant approves: `{"action": "FIGHT", "note": "...", "offerAmount": 20}` (all optional) |
| `POST` | `/api/store/orders` | Demo store: create a sandbox order `{"amount": 49.99, "description": "...", "scenario": "DELIVERED"}` |
| `POST` | `/api/store/orders/{paypalOrderId}/capture` | Capture after the buyer approves |
| `POST` | `/api/store/orders/import` | Import an order paid outside the app `{"paypalOrderId": "...", "scenario": "DELIVERED"}` |
| `GET` | `/api/store/orders` | Demo-store orders |

## Configuration

| Variable | Default | Notes |
| --- | --- | --- |
| `PAYPAL_CLIENT_ID` / `PAYPAL_CLIENT_SECRET` | — | Sandbox app credentials (US business sandbox account) |
| `PAYPAL_WEBHOOK_ID` | — | From `scripts/sandbox/register-webhook.sh` |
| `PAYPAL_BASE_URL` | `https://api-m.sandbox.paypal.com` | |
| `PAYPAL_SKIP_WEBHOOK_VERIFICATION` | `false` | `true` only for local tests without a registered webhook |
| `LLM_BASE_URL` | `https://api.anthropic.com/v1` | Any OpenAI-compatible endpoint |
| `LLM_MODEL` | `claude-sonnet-5-5` | e.g. `gemini-3.8-flash` with the Gemini base URL |
| `LLM_API_KEY` | — | Empty = rule-based packs, no AI |
| `PORT` | `8080` | |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Dashboard origin(s), comma-separated |
| `DATABASE_URL` / `REDIS_URL` / `KAFKA_BOOTSTRAP_SERVERS` | local Docker | |

## License

[MIT](LICENSE)
