#!/usr/bin/env bash
# Arranca os 4 microsserviços (N réplicas de cada) em background, cada réplica com run-instance.sh.
# Uso (a partir de code/):  ./scripts/start-all.sh [replicas 1-3, omissão 3] [perfis-extra] [opções-jvm, omissão -Xmx320m]
#   ./scripts/start-all.sh                     # 3 réplicas de cada serviço (12 JVMs)
#   ./scripts/start-all.sh 1                   # 1 réplica de cada (máquinas com pouca memória)
#   ./scripts/start-all.sh 3 demo -Xmx256m
#   ./scripts/start-all.sh 2 demo,tls          # HTTPS (gerar antes: ./scripts/generate-dev-certs.sh)
# Logs e PIDs em code/logs/ (ignorado pelo git): <servico>-<n>.out (consola) e <servico>-<n>.pid.
# Parar tudo: ./scripts/stop-all.sh     WAIT_SECONDS=0 não espera pelo health.
set -euo pipefail
REPLICAS="${1:-3}"
EXTRA="${2:-}"
MEMORY="${3:--Xmx320m}"
WAIT_SECONDS="${WAIT_SECONDS:-150}"
case "$REPLICAS" in 1|2|3) ;; *) echo "replicas tem de ser 1, 2 ou 3 (recebido: $REPLICAS)" >&2; exit 1 ;; esac
cd "$(dirname "$0")/.."
. scripts/lib/dev-certs.sh

# Porta base de cada serviço; a réplica n usa base + 100*(n-1) (docs/2-containers/Ports.md)
SERVICES="aircraftmanagement:8081 maintenance:8082 airports:8083 flightroutes:8084"

assert_dev_certs "$EXTRA"
BUILD=""
for entry in $SERVICES; do
  svc="${entry%%:*}"
  jar_outdated "$svc/target/$svc-0.0.1-SNAPSHOT.jar" "$EXTRA" && BUILD="$BUILD $svc"
done
if [ -n "$BUILD" ]; then
  echo "A compilar (JARs em falta ou mais antigos do que os certificados:$BUILD)..."
  ./mvnw -q -DskipTests package
fi

mkdir -p logs
for f in logs/*.pid; do
  [ -e "$f" ] || continue
  if kill -0 "$(cat "$f")" 2>/dev/null; then
    echo "Já há réplicas a correr ($f). Parar primeiro com ./scripts/stop-all.sh" >&2
    exit 1
  fi
done

SCHEME=http
has_tls_profile "$EXTRA" && SCHEME=https
export JAVA_OPTS="$MEMORY"   # lido por run-instance.sh
ROWS=()
for entry in $SERVICES; do
  svc="${entry%%:*}"; base="${entry##*:}"
  for i in $(seq 1 "$REPLICAS"); do
    nohup ./scripts/run-instance.sh "$svc" "$i" "$EXTRA" > "logs/$svc-$i.out" 2>&1 &
    echo $! > "logs/$svc-$i.pid"
    ROWS+=("$svc $i $! $SCHEME://localhost:$((base + 100 * (i - 1)))")
  done
done
echo "${#ROWS[@]} réplicas a arrancar ($MEMORY, perfis instanceN${EXTRA:+,$EXTRA})."

health() {
  local pid="$1" url="$2"
  kill -0 "$pid" 2>/dev/null || { echo EXITED; return; }
  [ "$(curl -s -k -o /dev/null -w '%{http_code}' --max-time 2 "$url/actuator/health")" = 200 ] && echo UP || echo DOWN
}

STATES=()
if [ "$WAIT_SECONDS" -gt 0 ]; then
  echo "A aguardar /actuator/health (até ${WAIT_SECONDS}s)..."
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

printf '\n%-20s %-7s %-8s %-24s %s\n' SERVICE REPLICA PID URL HEALTH
idx=0; failed=""
for row in "${ROWS[@]}"; do
  set -- $row
  state="${STATES[$idx]:-}"
  printf '%-20s %-7s %-8s %-24s %s\n' "$1" "$2" "$3" "$4" "$state"
  [ -n "$state" ] && [ "$state" != UP ] && failed="$failed $1-$2"
  idx=$((idx + 1))
done
echo
echo "Swagger: <URL>/swagger-ui.html   Logs: code/logs/<servico>-<n>.out   Parar: ./scripts/stop-all.sh"
[ -z "$failed" ] || echo "Réplicas sem health UP:$failed. Ver os .out em code/logs/." >&2
