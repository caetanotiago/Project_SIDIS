# Funções partilhadas pelos scripts de arranque (dot-source: . "$PSScriptRoot\lib\dev-certs.ps1").
# O certificado TLS de desenvolvimento não está no git: é gerado por scripts\generate-dev-certs.ps1
# para common\src\main\resources\tls\ e fica dentro do JAR do common (classpath:tls/...).

$script:DevCertsDir = Join-Path $PSScriptRoot "..\..\common\src\main\resources\tls"
$script:DevCertFiles = @("aisafe-dev-keystore.p12", "aisafe-dev-truststore.p12")

# $true se a lista de perfis (separados por vírgulas) inclui "tls".
function Test-TlsProfile([string]$Profiles) {
    return [bool](($Profiles -split ",") | ForEach-Object { $_.Trim() } | Where-Object { $_ -eq "tls" })
}

# Com o perfil tls, pára com instruções se os certificados não existirem.
function Assert-DevCerts([string]$Profiles) {
    if (-not (Test-TlsProfile $Profiles)) { return }
    $missing = $script:DevCertFiles | Where-Object { -not (Test-Path (Join-Path $script:DevCertsDir $_)) }
    if ($missing) {
        Write-Host "Perfil tls: faltam os certificados de desenvolvimento ($($missing -join ', '))." -ForegroundColor Red
        Write-Host "Gerar (a partir de code\):  .\scripts\generate-dev-certs.ps1" -ForegroundColor Red
        Write-Host "e voltar a correr este script (os JARs sao recompilados automaticamente)." -ForegroundColor Red
        throw "Certificados TLS de desenvolvimento em falta."
    }
}

# $true se o JAR não existe ou se, com o perfil tls, o certificado é mais recente do que o JAR
# (o JAR foi compilado antes de os certificados serem gerados e não os contém).
function Test-JarOutdated([string]$Jar, [string]$Profiles) {
    if (-not (Test-Path $Jar)) { return $true }
    if (-not (Test-TlsProfile $Profiles)) { return $false }
    $keystore = Join-Path $script:DevCertsDir $script:DevCertFiles[0]
    return (Get-Item $keystore).LastWriteTime -gt (Get-Item $Jar).LastWriteTime
}
