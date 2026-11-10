#!/usr/bin/env bash
# Pára todas as réplicas arrancadas por start-all.sh (ou start-replicas.sh), a partir de code/logs/*.pid.
# Uso (a partir de code/):  ./scripts/stop-all.sh [--broker]
#   --broker  also stops the RabbitMQ container of infra/docker-compose.rabbitmq.yml (its data volume is kept)
set -uo pipefail
cd "$(dirname "$0")/.."
. scripts/lib/rabbitmq.sh
STOP_BROKER=0
[ "${1:-}" = "--broker" ] && STOP_BROKER=1
shopt -s nullglob
files=(logs/*.pid)
[ ${#files[@]} -gt 0 ] || echo "Nada a parar (não há ficheiros logs/*.pid)."

stop_pid() {
  local pid="$1"
  # Git Bash no Windows: o PID é do MSYS; a JVM é um processo Windows → terminar a árvore com taskkill
  if [ -r "/proc/$pid/winpid" ] && command -v taskkill >/dev/null 2>&1; then
    taskkill //PID "$(cat "/proc/$pid/winpid")" //T //F >/dev/null 2>&1
    return
  fi
  kill "$pid" 2>/dev/null
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    kill -0 "$pid" 2>/dev/null || return
    sleep 1
  done
  kill -9 "$pid" 2>/dev/null
}

for f in "${files[@]}"; do
  pid="$(cat "$f")"
  name="$(basename "$f" .pid)"
  if kill -0 "$pid" 2>/dev/null; then
    stop_pid "$pid"
    echo "Parado $name (PID $pid)"
  else
    echo "$name já não estava a correr (PID $pid)"
  fi
  rm -f "$f"
done
if [ "$STOP_BROKER" -eq 1 ]; then
  stop_broker
else
  echo "RabbitMQ left running (stop it too with: ./scripts/stop-all.sh --broker)."
fi
