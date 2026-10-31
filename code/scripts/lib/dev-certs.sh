# Funções partilhadas pelos scripts de arranque (source "$(dirname "$0")/lib/dev-certs.sh").
# O certificado TLS de desenvolvimento não está no git: é gerado por scripts/generate-dev-certs.sh
# para common/src/main/resources/tls/ e fica dentro do JAR do common (classpath:tls/...).
# Assume que o diretório atual é code/.

DEV_CERTS_DIR="common/src/main/resources/tls"
DEV_CERT_FILES="aisafe-dev-keystore.p12 aisafe-dev-truststore.p12"

# Sucesso (0) se a lista de perfis (separados por vírgulas) inclui "tls".
has_tls_profile() {
  case ",${1:-}," in *,tls,*) return 0 ;; *) return 1 ;; esac
}

# Com o perfil tls, pára com instruções se os certificados não existirem.
assert_dev_certs() {
  has_tls_profile "${1:-}" || return 0
  local missing="" f
  for f in $DEV_CERT_FILES; do
    [ -f "$DEV_CERTS_DIR/$f" ] || missing="$missing $f"
  done
  if [ -n "$missing" ]; then
    echo "Perfil tls: faltam os certificados de desenvolvimento:$missing" >&2
    echo "Gerar (a partir de code/):  ./scripts/generate-dev-certs.sh" >&2
    echo "e voltar a correr este script (os JARs são recompilados automaticamente)." >&2
    exit 1
  fi
}

# Sucesso (0) se o JAR não existe ou se, com o perfil tls, o certificado é mais recente do que o JAR
# (o JAR foi compilado antes de os certificados serem gerados e não os contém).
jar_outdated() {
  local jar="$1" profiles="${2:-}"
  [ -f "$jar" ] || return 0
  has_tls_profile "$profiles" || return 1
  [ "$DEV_CERTS_DIR/aisafe-dev-keystore.p12" -nt "$jar" ]
}
