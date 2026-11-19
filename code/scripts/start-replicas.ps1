# Starts N replicas (default 3) of ONE microservice, each in its own window, through run-instance.ps1
# (which loads infra\.env).
# Usage (from code\):  .\scripts\start-replicas.ps1 -Service flightroutes [-Profiles demo] [-Replicas 2]
param(
    [Parameter(Mandatory = $true)][ValidateSet("aircraftmanagement", "maintenance", "airports", "flightroutes")][string]$Service,
    [string]$Profiles = "",
    [ValidateRange(1, 3)][int]$Replicas = 3
)
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "lib\dev-certs.ps1")
. (Join-Path $PSScriptRoot "lib\rabbitmq.ps1")
$codeDir = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $codeDir
Assert-DevCerts $Profiles
# Loaded here too, so the child windows inherit RABBITMQ_* even if run-instance.ps1 is edited later.
if (-not (Import-BrokerEnv)) { Write-Host "Warning: infra\.env not found; RABBITMQ_* must come from the environment." -ForegroundColor Yellow }
.\mvnw.cmd -q -pl $Service -am -DskipTests package
foreach ($i in 1..$Replicas) {
    # Quoted path: Windows PowerShell's Start-Process joins the arguments without quotes, so a folder with
    # spaces (e.g. C:\Users\First Last) would split -File in two and the window would close immediately.
    $argList = "-NoExit", "-ExecutionPolicy", "Bypass", "-File", "`"$(Join-Path $PSScriptRoot "run-instance.ps1")`"", "-Service", $Service, "-Instance", $i
    if ($Profiles) { $argList += "-Profiles", $Profiles }
    Start-Process powershell -ArgumentList $argList -WorkingDirectory $codeDir
}
Write-Host "$Replicas replicas of $Service starting. Liveness: /actuator/health/liveness   Readiness (includes RabbitMQ): /actuator/health/readiness"
