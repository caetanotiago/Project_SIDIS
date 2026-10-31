# Arranca UMA réplica de um microsserviço.
# Uso (a partir de code\):  .\scripts\run-instance.ps1 -Service flightroutes -Instance 2 [-Profiles demo] [-JvmOptions "-Xmx320m"]
# Opções da JVM: -JvmOptions ou a variável de ambiente JAVA_OPTS (usada pelo start-all.ps1).
param(
    [Parameter(Mandatory = $true)][ValidateSet("aircraftmanagement", "maintenance", "airports", "flightroutes")][string]$Service,
    [Parameter(Mandatory = $true)][ValidateRange(1, 3)][int]$Instance,
    [string]$Profiles = "",
    [string]$JvmOptions = $env:JAVA_OPTS
)
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "lib\dev-certs.ps1")
Set-Location (Join-Path $PSScriptRoot "..")
Assert-DevCerts $Profiles
$jar = "$Service\target\$Service-0.0.1-SNAPSHOT.jar"
if (Test-JarOutdated $jar $Profiles) { .\mvnw.cmd -q -pl $Service -am -DskipTests package }
$active = "instance$Instance"
if ($Profiles) { $active = "$active,$Profiles" }
$jvm = @()
if ($JvmOptions) { $jvm = $JvmOptions.Trim() -split "\s+" }
Write-Host "A arrancar $Service com os perfis $active $JvmOptions"
java @jvm -jar $jar "--spring.profiles.active=$active"
