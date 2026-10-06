# Gera o certificado TLS de DESENVOLVIMENTO partilhado por todos os serviços (perfil "tls").
# Uso (a partir de code\):  .\scripts\generate-dev-certs.ps1 [-Password changeit]
param([string]$Password = "changeit")
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
