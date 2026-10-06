#!/usr/bin/env bash
# Arranca as 3 réplicas de um microsserviço em background (logs em code/logs/).
# Uso (a partir de code/):  ./scripts/start-replicas.sh flightroutes [perfis-extra]
# Parar:                     kill $(cat logs/<servico>-*.pid)
set -euo pipefail
SERVICE="${1:?servico em falta}"
EXTRA="${2:-}"
cd "$(dirname "$0")/.."
./mvnw -q -pl "$SERVICE" -am -DskipTests package
mkdir -p logs
for i in 1 2 3; do
  PROFILES="instance$i${EXTRA:+,$EXTRA}"
  nohup java -jar "$SERVICE/target/$SERVICE-0.0.1-SNAPSHOT.jar" --spring.profiles.active="$PROFILES" \
    > "logs/$SERVICE-$i.out" 2>&1 &
  echo $! > "logs/$SERVICE-$i.pid"
  echo "$SERVICE réplica $i (perfis $PROFILES) PID $!"
done
