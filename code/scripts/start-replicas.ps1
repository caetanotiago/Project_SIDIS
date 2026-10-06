# Arranca as 3 réplicas de um microsserviço, cada uma na sua janela.
# Uso (a partir de code\):  .\scripts\start-replicas.ps1 -Service flightroutes [-Profiles demo]
param(
    [Parameter(Mandatory = $true)][ValidateSet("aircraftmanagement", "maintenance", "airports", "flightroutes")][string]$Service,
    [string]$Profiles = "",
    [int]$Replicas = 3
)
$ErrorActionPreference = "Stop"
$codeDir = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $codeDir
.\mvnw.cmd -q -pl $Service -am -DskipTests package
foreach ($i in 1..$Replicas) {
    $argList = "-NoExit", "-File", (Join-Path $PSScriptRoot "run-instance.ps1"), "-Service", $Service, "-Instance", $i
    if ($Profiles) { $argList += "-Profiles", $Profiles }
    Start-Process powershell -ArgumentList $argList -WorkingDirectory $codeDir
}
Write-Host "$Replicas réplicas de $Service a arrancar. Health: /actuator/health"
