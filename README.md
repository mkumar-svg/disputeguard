# DisputeGuard

**An AI agent for PayPal merchant disputes.** When a buyer opens a dispute, DisputeGuard gathers the
evidence, scores the chance of winning, drafts the response, and submits it through the PayPal
Disputes API once the merchant approves.

Built for the [PayPal AI Hackathon](https://paypalaihackathon.devpost.com/).

> Status: early build. Webhook intake, Kafka pipeline and dispute sync work; the AI agent,
> dashboard and submit flow are in progress.

## How it works

```
PayPal Sandbox ──webhook──▶ Webhook receiver ──▶ Kafka (dispute.created / dispute.updated)
                            verify signature              │
                            dedupe in Redis               ▼
                                                Enrichment worker ── GET dispute, order, tracking, chats
                                                          │
                                                          ▼
                                                     AI agent ── classify, score, draft   (in progress)
                                                          │
                                                          ▼
Merchant dashboard ── review & approve ──▶ Submit service ──▶ PayPal provide-evidence     (in progress)
```

The AI never writes to PayPal on its own: every `provide-evidence`, `accept-claim` or `make-offer`
call needs the merchant's approval.

## How we use PayPal

| PayPal API | Used for |
| --- | --- |
| OAuth 2.0 (`/v1/oauth2/token`) | Access tokens, cached in Redis until shortly before expiry |
| Webhooks (`CUSTOMER.DISPUTE.CREATED / UPDATED / RESOLVED`) | Trigger the pipeline |
| Webhooks (`/v1/notifications/verify-webhook-signature`) | Reject forged deliveries |
| Disputes API (`/v1/customer/disputes`, `/{id}`) | Dispute details: reason, amount, status, response deadline |
| Disputes API (`provide-evidence`, `accept-claim`, `make-offer`) | Submit the evidence pack or settle (in progress) |
| Orders API (`/v2/checkout/orders`) | Real sandbox purchases for the demo store |

Everything runs against the PayPal **Sandbox**.

## How we use AI

_In progress._ A tool-calling LLM agent classifies the dispute reason, calls tools (`getOrder`,
`getTracking`, `getChatLogs`) to gather evidence, scores it against a per-reason checklist for an
explainable win chance, and drafts the response note. Output is validated against a JSON schema.

## Tech stack

Java 21 · Spring Boot 3 · Apache Kafka · Redis · PostgreSQL (Flyway) · Docker Compose ·
React (dashboard, in progress) · Render (deployment)

## Run locally

Requirements: Java 21, Maven, Docker, `jq`, and a PayPal developer account.

```bash
cp .env.example .env                 # add your sandbox Client ID and Secret
docker compose up -d                 # Postgres, Redis, Kafka
set -a; source .env; set +a
mvn spring-boot:run                  # app on http://localhost:8080
```

Check it is up: `curl localhost:8080/actuator/health`

To get a real sandbox dispute into the app, follow [docs/SANDBOX_TESTING.md](docs/SANDBOX_TESTING.md).

## API

| Method | Path | What it does |
| --- | --- | --- |
| `POST` | `/webhooks/paypal` | PayPal webhook receiver |
| `GET` | `/api/disputes` | Disputes stored locally, newest first |
| `POST` | `/api/disputes/sync` | Pull all disputes from PayPal (works without webhooks) |
| `POST` | `/api/disputes/{id}/sync` | Re-sync one dispute |

## Configuration

| Variable | Default | Notes |
| --- | --- | --- |
| `PAYPAL_CLIENT_ID` / `PAYPAL_CLIENT_SECRET` | — | Sandbox app credentials |
| `PAYPAL_WEBHOOK_ID` | — | From `scripts/sandbox/register-webhook.sh` |
| `PAYPAL_BASE_URL` | `https://api-m.sandbox.paypal.com` | |
| `PAYPAL_SKIP_WEBHOOK_VERIFICATION` | `false` | `true` only for PayPal's webhook simulator |
| `DATABASE_URL` / `REDIS_URL` / `KAFKA_BOOTSTRAP_SERVERS` | local Docker | |

## License

[MIT](LICENSE)
