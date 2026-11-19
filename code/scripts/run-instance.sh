#!/usr/bin/env bash
# Starts ONE replica of a microservice in the foreground.
# Usage (from code/):  ./scripts/run-instance.sh <service> <n> [extra-profiles]
#   ./scripts/run-instance.sh flightroutes 2 demo        -> profiles instance2,demo (port 8184)
#   ./scripts/run-instance.sh flightroutes 1 demo,tls    -> HTTPS
#   JAVA_OPTS="-Xmx320m" ./scripts/run-instance.sh airports 1   -> JVM options
# Services: aircraftmanagement | maintenance | airports | flightroutes (ports in docs/2-containers/Ports.md)
# RabbitMQ credentials (RABBITMQ_*) are read from infra/.env unless they are already set in the environment.
set -euo pipefail
SERVICE="${1:?service missing}"
N="${2:?instance number missing (1, 2 or 3)}"
EXTRA="${3:-}"
cd "$(dirname "$0")/.."
. scripts/lib/dev-certs.sh
. scripts/lib/rabbitmq.sh
assert_dev_certs "$EXTRA"
if ! import_broker_env && [ "${AISAFE_MESSAGING_ENABLED:-}" != "false" ] && [ -z "${RABBITMQ_USER:-}" ]; then
  echo "Warning: infra/.env not found and RABBITMQ_USER is not set; the replica will not reach RabbitMQ." >&2
  echo "         Copy infra/.env.example to infra/.env, or set AISAFE_MESSAGING_ENABLED=false." >&2
fi
JAR="$SERVICE/target/$SERVICE-0.0.1-SNAPSHOT.jar"
if jar_outdated "$JAR" "$EXTRA"; then
  ./mvnw -q -pl "$SERVICE" -am -DskipTests package
fi
PROFILES="instance$N${EXTRA:+,$EXTRA}"
echo "Starting $SERVICE with profiles $PROFILES ${JAVA_OPTS:-}"
# shellcheck disable=SC2086  # JAVA_OPTS holds several space-separated options
exec java ${JAVA_OPTS:-} -jar "$JAR" --spring.profiles.active="$PROFILES"
