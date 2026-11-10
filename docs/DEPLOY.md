# Deploy on Render

The repo has a Render Blueprint (`render.yaml`) that creates three things:

| Resource | What it is |
| --- | --- |
| `disputeguard` | Web service built from the `Dockerfile` (API + dashboard on one URL) |
| `disputeguard-db` | Free Postgres |
| `disputeguard-redis` | Free Key Value (Redis) for the PayPal token cache and webhook de-duplication |

Render has no managed Kafka, so the hosted demo runs with `EVENTS_MODE=inline`: verified webhooks
are processed in-process on a background thread instead of going through Kafka. Locally,
Docker Compose runs the full Kafka pipeline (`EVENTS_MODE=kafka`, the default).

## Steps

1. Push the repo to GitHub.
2. On dashboard.render.com: **New → Blueprint**, pick the repo. Render reads `render.yaml`.
3. Fill in the secrets it asks for: `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET`, `LLM_API_KEY`.
   Leave `PAYPAL_WEBHOOK_ID` empty for now.
4. Apply. The first Docker build takes several minutes. When it is live, open
   `https://<your-service>.onrender.com/actuator/health` (expect `{"status":"UP"}`), then the root URL
   for the dashboard.
5. Register the webhook against the public URL (from your machine, with `.env` loaded):
   ```bash
   ./scripts/sandbox/register-webhook.sh https://<your-service>.onrender.com/webhooks/paypal
   ```
   Put the returned `id` into the service's `PAYPAL_WEBHOOK_ID` environment variable and save
   (Render redeploys).
6. Test: open a dispute as the sandbox buyer. Within a few minutes it appears on the dashboard
   with its evidence pack, without pressing anything.

## Notes

- Free web services sleep after a period without traffic; the first request then takes about a
  minute. Open the URL once before a demo.
- Free Postgres on Render expires after a limited time; fine for the hackathon judging window.
- The database starts empty: place demo orders from the dashboard's Demo store tab, or import
  existing captured orders with `POST /api/store/orders/import`.
