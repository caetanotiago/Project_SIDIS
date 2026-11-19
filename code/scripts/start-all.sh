#!/usr/bin/env bash
# Starts the 4 microservices (N replicas each) in the background, each replica through run-instance.sh.
# Usage (from code/):  ./scripts/start-all.sh [replicas 1-3, default 3] [extra-profiles] [jvm-options, default -Xmx320m]
#   ./scripts/start-all.sh                     # 3 replicas of each service (12 JVMs)
#   ./scripts/start-all.sh 1                   # 1 replica of each (low-memory machines)
#   ./scripts/start-all.sh 3 demo -Xmx256m
#   ./scripts/start-all.sh 2 demo,tls          # HTTPS (generate first: ./scripts/generate-dev-certs.sh)
# Logs and PIDs in code/logs/ (ignored by git): <service>-<n>.out (console) and <service>-<n>.pid.
# Stop everything: ./scripts/stop-all.sh     WAIT_SECONDS=0 does not wait for the health checks.
#
# "UP" means LIVENESS (/actuator/health/liveness): the JVM is serving HTTP. RabbitMQ is only part of
# READINESS (/actuator/health/readiness), so a replica whose broker is down is still reported UP here.
set -euo pipefail
REPLICAS="${1:-3}"
EXTRA="${2:-}"
MEMORY="${3:--Xmx320m}"
WAIT_SECONDS="${WAIT_SECONDS:-150}"
case "$REPLICAS" in 1|2|3) ;; *) echo "replicas must be 1, 2 or 3 (got: $REPLICAS)" >&2; exit 1 ;; esac
cd "$(dirname "$0")/.."
. scripts/lib/dev-certs.sh
. scripts/lib/rabbitmq.sh

# Base port of each service; replica n uses base + 100*(n-1) (docs/2-containers/Ports.md)
SERVICES="aircraftmanagement:8081 maintenance:8082 airports:8083 flightroutes:8084"

assert_dev_certs "$EXTRA"
# RabbitMQ must be reachable before any replica starts (started with docker compose if needed).
# Exports infra/.env, so every replica inherits RABBITMQ_*. Skip with AISAFE_MESSAGING_ENABLED=false.
assert_broker
BUILD=""
for entry in $SERVICES; do
  svc="${entry%%:*}"
  jar_outdated "$svc/target/$svc-0.0.1-SNAPSHOT.jar" "$EXTRA" && BUILD="$BUILD $svc"
done
if [ -n "$BUILD" ]; then
  echo "Building (JARs missing or older than the certificates:$BUILD)..."
  ./mvnw -q -DskipTests package
fi

mkdir -p logs
for f in logs/*.pid; do
  [ -e "$f" ] || continue
  if kill -0 "$(cat "$f")" 2>/dev/null; then
    echo "Replicas are already running ($f). Stop them first with ./scripts/stop-all.sh" >&2
    exit 1
  fi
done

SCHEME=http
has_tls_profile "$EXTRA" && SCHEME=https
export JAVA_OPTS="$MEMORY"   # read by run-instance.sh
ROWS=()
for entry in $SERVICES; do
  svc="${entry%%:*}"; base="${entry##*:}"
  for i in $(seq 1 "$REPLICAS"); do
    nohup ./scripts/run-instance.sh "$svc" "$i" "$EXTRA" > "logs/$svc-$i.out" 2>&1 &
    echo $! > "logs/$svc-$i.pid"
    ROWS+=("$svc $i $! $SCHEME://localhost:$((base + 100 * (i - 1)))")
  done
done
echo "${#ROWS[@]} replicas starting ($MEMORY, profiles instanceN${EXTRA:+,$EXTRA})."

http_code() {
  curl -s -k -o /dev/null -w '%{http_code}' --max-time 2 "$1" || true
}

# Liveness probe; services that do not expose the probe groups (404) fall back to /actuator/health.
health() {
  local pid="$1" url="$2" code
  kill -0 "$pid" 2>/dev/null || { echo EXITED; return; }
  code="$(http_code "$url/actuator/health/liveness")"
  [ "$code" = 404 ] && code="$(http_code "$url/actuator/health")"
  [ "$code" = 200 ] && echo UP || echo DOWN
}

STATES=()
if [ "$WAIT_SECONDS" -gt 0 ]; then
  echo "Waiting for /actuator/health/liveness (up to ${WAIT_SECONDS}s)..."
  deadline=$((SECONDS + WAIT_SECONDS))
  while :; do
    sleep 5
    STATES=(); pending=0
    for row in "${ROWS[@]}"; do
      set -- $row
      s="$(health "$3" "$4")"; STATES+=("$s")
      [ "$s" = DOWN ] && pending=$((pending + 1))
    done
    [ "$pending" -eq 0 ] || [ "$SECONDS" -ge "$deadline" ] && break
  done
fi

printf '\n%-20s %-7s %-8s %-24s %s\n' SERVICE REPLICA PID URL LIVENESS
idx=0; failed=""
for row in "${ROWS[@]}"; do
  set -- $row
  state="${STATES[$idx]:-}"
  printf '%-20s %-7s %-8s %-24s %s\n' "$1" "$2" "$3" "$4" "$state"
  [ -n "$state" ] && [ "$state" != UP ] && failed="$failed $1-$2"
  idx=$((idx + 1))
done
echo
echo "Swagger: <URL>/swagger-ui.html   Readiness (incl. RabbitMQ): <URL>/actuator/health/readiness"
echo "Logs: code/logs/<service>-<n>.out   Stop: ./scripts/stop-all.sh"
[ -z "$failed" ] || echo "Replicas not live:$failed. See the .out files in code/logs/." >&2
