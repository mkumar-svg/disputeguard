#!/usr/bin/env bash
# One command to start DisputeGuard locally: infrastructure, then the app.
# Usage: ./scripts/start.sh
set -euo pipefail
cd "$(dirname "$0")/.."

# Containers from other projects often hold the same ports; stop them if they are running.
docker stop kafka kafka-ui postgres redis >/dev/null 2>&1 || true

docker compose up -d
echo "Waiting for Postgres and Kafka..."
for i in $(seq 1 30); do
  if docker compose ps postgres | grep -q healthy; then break; fi
  sleep 2
done
sleep 5   # Kafka needs a few seconds more than Postgres
docker compose ps

if [[ -f .env ]]; then
  sed -i 's/\r$//' .env          # Notepad saves Windows line endings
  set -a; source .env; set +a
fi

# The dashboard is served by the app when it has been built (frontend: npm install && npm run build).
mvn spring-boot:run
