# Gera o certificado TLS de DESENVOLVIMENTO partilhado por todos os serviços (perfil "tls").
# Uso (a partir de code\):  .\scripts\generate-dev-certs.ps1 [-Password changeit]
# Os ficheiros e a password têm de coincidir com application-tls.properties de cada serviço:
#   server.ssl.key-store=classpath:tls/aisafe-dev-keystore.p12 (alias aisafe, PKCS12)
#   aisafe.tls.trust-store=classpath:tls/aisafe-dev-truststore.p12
#   password = ${AISAFE_TLS_PASSWORD:changeit}  -> por omissão usa-se a mesma variável de ambiente
# Não são versionados (.gitignore). Ficam no JAR do common: depois de os gerar é preciso recompilar.
param([string]$Password = $(if ($env:AISAFE_TLS_PASSWORD) { $env:AISAFE_TLS_PASSWORD } else { "changeit" }))
$ErrorActionPreference = "Stop"
$dir = Join-Path $PSScriptRoot "..\common\src\main\resources\tls"
New-Item -ItemType Directory -Force $dir | Out-Null
Remove-Item -ErrorAction SilentlyContinue "$dir\aisafe-dev-keystore.p12", "$dir\aisafe-dev-truststore.p12", "$dir\aisafe-dev.crt"

keytool -genkeypair -alias aisafe -keyalg RSA -keysize 2048 -validity 3650 `
  -storetype PKCS12 -keystore "$dir\aisafe-dev-keystore.p12" -storepass $Password `
  -dname "CN=localhost, OU=SIDIS, O=ISEP, C=PT" -ext "SAN=dns:localhost,ip:127.0.0.1"
keytool -exportcert -alias aisafe -keystore "$dir\aisafe-dev-keystore.p12" -storepass $Password `
  -rfc -file "$dir\aisafe-dev.crt"
keytool -importcert -noprompt -alias aisafe -file "$dir\aisafe-dev.crt" `
  -storetype PKCS12 -keystore "$dir\aisafe-dev-truststore.p12" -storepass $Password
Write-Host "Certificados gerados em $dir"
Write-Host "Recompilar para os incluir nos JARs:  .\mvnw.cmd -q -DskipTests package"
