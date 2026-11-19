# Starts ONE replica of a microservice in the foreground.
# Usage (from code\):  .\scripts\run-instance.ps1 -Service flightroutes -Instance 2 [-Profiles demo] [-JvmOptions "-Xmx320m"]
# JVM options: -JvmOptions or the JAVA_OPTS environment variable (set by start-all.ps1).
# RabbitMQ credentials (RABBITMQ_*) are read from infra\.env unless they are already set in the environment.
param(
    [Parameter(Mandatory = $true)][ValidateSet("aircraftmanagement", "maintenance", "airports", "flightroutes")][string]$Service,
    [Parameter(Mandatory = $true)][ValidateRange(1, 3)][int]$Instance,
    [string]$Profiles = "",
    [string]$JvmOptions = $env:JAVA_OPTS
)
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "lib\dev-certs.ps1")
. (Join-Path $PSScriptRoot "lib\rabbitmq.ps1")
Set-Location (Join-Path $PSScriptRoot "..")
Assert-DevCerts $Profiles
$hasEnv = Import-BrokerEnv
if (-not $hasEnv -and $env:AISAFE_MESSAGING_ENABLED -ne "false" -and -not $env:RABBITMQ_USER) {
    Write-Host "Warning: infra\.env not found and RABBITMQ_USER is not set; the replica will not reach RabbitMQ." -ForegroundColor Yellow
    Write-Host "         Copy infra\.env.example to infra\.env, or set `$env:AISAFE_MESSAGING_ENABLED=`"false`"." -ForegroundColor Yellow
}
$jar = "$Service\target\$Service-0.0.1-SNAPSHOT.jar"
if (Test-JarOutdated $jar $Profiles) { .\mvnw.cmd -q -pl $Service -am -DskipTests package }
$active = "instance$Instance"
if ($Profiles) { $active = "$active,$Profiles" }
$jvm = @()
if ($JvmOptions) { $jvm = $JvmOptions.Trim() -split "\s+" }
Write-Host "Starting $Service with profiles $active $JvmOptions"
java @jvm -jar $jar "--spring.profiles.active=$active"
