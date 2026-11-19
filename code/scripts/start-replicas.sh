#!/usr/bin/env bash
# Starts N replicas (default 3) of ONE microservice in the background, each through run-instance.sh
# (which loads infra/.env). Logs and PIDs in code/logs/.
# Usage (from code/):  ./scripts/start-replicas.sh flightroutes [extra-profiles] [replicas 1-3]
#   ./scripts/start-replicas.sh flightroutes demo 2
# Stop:                 kill $(cat logs/<service>-*.pid)
set -euo pipefail
SERVICE="${1:?service missing}"
EXTRA="${2:-}"
REPLICAS="${3:-3}"
case "$REPLICAS" in 1|2|3) ;; *) echo "replicas must be 1, 2 or 3 (got: $REPLICAS)" >&2; exit 1 ;; esac
cd "$(dirname "$0")/.."
. scripts/lib/dev-certs.sh
. scripts/lib/rabbitmq.sh
assert_dev_certs "$EXTRA"
import_broker_env || echo "Warning: infra/.env not found; RABBITMQ_* must come from the environment." >&2
./mvnw -q -pl "$SERVICE" -am -DskipTests package
mkdir -p logs
for i in $(seq 1 "$REPLICAS"); do
  nohup ./scripts/run-instance.sh "$SERVICE" "$i" "$EXTRA" > "logs/$SERVICE-$i.out" 2>&1 &
  echo $! > "logs/$SERVICE-$i.pid"
  echo "$SERVICE replica $i (profiles instance$i${EXTRA:+,$EXTRA}) PID $!"
done
echo "Liveness: <url>/actuator/health/liveness   Readiness (includes RabbitMQ): <url>/actuator/health/readiness"
