#!/usr/bin/env bash
# Arranca UMA réplica de um microsserviço.
# Uso (a partir de code/):  ./scripts/run-instance.sh <servico> <n> [perfis-extra]
#   ./scripts/run-instance.sh flightroutes 2 demo        -> perfis instance2,demo (porta 8184)
#   ./scripts/run-instance.sh flightroutes 1 demo,tls    -> HTTPS
# Serviços: aircraftmanagement | maintenance | airports | flightroutes (portas em docs/2-containers/Ports.md)
set -euo pipefail
SERVICE="${1:?servico em falta}"
N="${2:?numero da instancia em falta (1, 2 ou 3)}"
EXTRA="${3:-}"
cd "$(dirname "$0")/.."
JAR="$SERVICE/target/$SERVICE-0.0.1-SNAPSHOT.jar"
if [ ! -f "$JAR" ]; then
  ./mvnw -q -pl "$SERVICE" -am -DskipTests package
fi
PROFILES="instance$N${EXTRA:+,$EXTRA}"
echo "A arrancar $SERVICE com os perfis $PROFILES"
exec java -jar "$JAR" --spring.profiles.active="$PROFILES"
