#!/usr/bin/env bash
# Gera o certificado TLS de DESENVOLVIMENTO partilhado por todos os serviços (perfil "tls").
# Cria em common/src/main/resources/tls/:
#   aisafe-dev-keystore.p12   — chave privada + certificado (server.ssl.key-store)
#   aisafe-dev-truststore.p12 — só o certificado (aisafe.tls.trust-store, usado pelos clientes)
# Uso (a partir de code/):  ./scripts/generate-dev-certs.sh [password]
set -euo pipefail
PASS="${1:-changeit}"
DIR="$(cd "$(dirname "$0")/.." && pwd)/common/src/main/resources/tls"
mkdir -p "$DIR"
rm -f "$DIR/aisafe-dev-keystore.p12" "$DIR/aisafe-dev-truststore.p12" "$DIR/aisafe-dev.crt"

keytool -genkeypair -alias aisafe -keyalg RSA -keysize 2048 -validity 3650 \
  -storetype PKCS12 -keystore "$DIR/aisafe-dev-keystore.p12" -storepass "$PASS" \
  -dname "CN=localhost, OU=SIDIS, O=ISEP, C=PT" -ext "SAN=dns:localhost,ip:127.0.0.1"
keytool -exportcert -alias aisafe -keystore "$DIR/aisafe-dev-keystore.p12" -storepass "$PASS" \
  -rfc -file "$DIR/aisafe-dev.crt"
keytool -importcert -noprompt -alias aisafe -file "$DIR/aisafe-dev.crt" \
  -storetype PKCS12 -keystore "$DIR/aisafe-dev-truststore.p12" -storepass "$PASS"
echo "Certificados gerados em $DIR"
