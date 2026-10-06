# Arranca UMA réplica de um microsserviço.
# Uso (a partir de code\):  .\scripts\run-instance.ps1 -Service flightroutes -Instance 2 [-Profiles demo]
param(
    [Parameter(Mandatory = $true)][ValidateSet("aircraftmanagement", "maintenance", "airports", "flightroutes")][string]$Service,
    [Parameter(Mandatory = $true)][ValidateRange(1, 3)][int]$Instance,
    [string]$Profiles = ""
)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
$jar = "$Service\target\$Service-0.0.1-SNAPSHOT.jar"
if (-not (Test-Path $jar)) { .\mvnw.cmd -q -pl $Service -am -DskipTests package }
$active = "instance$Instance"
if ($Profiles) { $active = "$active,$Profiles" }
Write-Host "A arrancar $Service com os perfis $active"
java -jar $jar "--spring.profiles.active=$active"
