#!/usr/bin/env bash
# Arranca UMA réplica de um microsserviço.
# Uso (a partir de code/):  ./scripts/run-instance.sh <servico> <n> [perfis-extra]
#   ./scripts/run-instance.sh flightroutes 2 demo        -> perfis instance2,demo (porta 8184)
#   ./scripts/run-instance.sh flightroutes 1 demo,tls    -> HTTPS
#   JAVA_OPTS="-Xmx320m" ./scripts/run-instance.sh airports 1   -> opções da JVM
# Serviços: aircraftmanagement | maintenance | airports | flightroutes (portas em docs/2-containers/Ports.md)
set -euo pipefail
SERVICE="${1:?servico em falta}"
N="${2:?numero da instancia em falta (1, 2 ou 3)}"
EXTRA="${3:-}"
cd "$(dirname "$0")/.."
. scripts/lib/dev-certs.sh
assert_dev_certs "$EXTRA"
JAR="$SERVICE/target/$SERVICE-0.0.1-SNAPSHOT.jar"
if jar_outdated "$JAR" "$EXTRA"; then
  ./mvnw -q -pl "$SERVICE" -am -DskipTests package
fi
PROFILES="instance$N${EXTRA:+,$EXTRA}"
echo "A arrancar $SERVICE com os perfis $PROFILES ${JAVA_OPTS:-}"
# shellcheck disable=SC2086  # JAVA_OPTS tem várias opções separadas por espaços
exec java ${JAVA_OPTS:-} -jar "$JAR" --spring.profiles.active="$PROFILES"
