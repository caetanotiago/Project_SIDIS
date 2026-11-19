# Starts the 4 microservices (N replicas each) in the background, each replica through run-instance.ps1.
# Usage (from code\):
#   .\scripts\start-all.ps1                               # 3 replicas of each service (12 JVMs)
#   .\scripts\start-all.ps1 -Replicas 1                   # 1 replica of each (low-memory machines)
#   .\scripts\start-all.ps1 -Profiles demo -Memory "-Xmx256m"   # (quoted: it starts with "-")
#   .\scripts\start-all.ps1 -Profiles demo,tls            # HTTPS (generate first: .\scripts\generate-dev-certs.ps1)
# Logs and PIDs in code\logs\ (ignored by git): <service>-<n>.out/.err (console) and <service>-<n>.pid.
# Stop everything: .\scripts\stop-all.ps1
#
# "UP" means LIVENESS (/actuator/health/liveness): the JVM is serving HTTP. RabbitMQ is only part of
# READINESS (/actuator/health/readiness), so a replica whose broker is down is still reported UP here.
param(
    [ValidateRange(1, 3)][int]$Replicas = 3,
    [string]$Profiles = "",
    [string]$Memory = "-Xmx320m",
    [int]$WaitSeconds = 150
)
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "lib\dev-certs.ps1")
. (Join-Path $PSScriptRoot "lib\rabbitmq.ps1")
$codeDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $codeDir

# Base port of each service; replica n uses base + 100*(n-1) (docs/2-containers/Ports.md)
$services = [ordered]@{ "aircraftmanagement" = 8081; "maintenance" = 8082; "airports" = 8083; "flightroutes" = 8084 }

Assert-DevCerts $Profiles
# RabbitMQ must be reachable before any replica starts (started with docker compose if needed).
# Loads infra\.env, so every replica inherits RABBITMQ_*. Skip with $env:AISAFE_MESSAGING_ENABLED="false".
Assert-Broker
$outdated = $services.Keys | Where-Object { Test-JarOutdated "$_\target\$_-0.0.1-SNAPSHOT.jar" $Profiles }
if ($outdated) {
    Write-Host "Building (JARs missing or older than the certificates: $($outdated -join ', '))..."
    .\mvnw.cmd -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "The build failed." }
}

New-Item -ItemType Directory -Force "logs" | Out-Null
$running = Get-ChildItem "logs\*.pid" -ErrorAction SilentlyContinue |
    Where-Object { Get-Process -Id ([int](Get-Content $_.FullName)) -ErrorAction SilentlyContinue }
if ($running) {
    throw "Replicas are already running ($($running.BaseName -join ', ')). Stop them first with .\scripts\stop-all.ps1"
}

$scheme = if (Test-TlsProfile $Profiles) { "https" } else { "http" }
$runInstance = Join-Path $PSScriptRoot "run-instance.ps1"
$env:JAVA_OPTS = $Memory   # inherited by the child processes and read by run-instance.ps1
$started = @()
foreach ($svc in $services.Keys) {
    foreach ($i in 1..$Replicas) {
        $argList = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$runInstance`"", "-Service", $svc, "-Instance", $i)
        if ($Profiles) { $argList += @("-Profiles", $Profiles) }
        $p = Start-Process powershell -ArgumentList $argList -WorkingDirectory $codeDir -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput "logs\$svc-$i.out" -RedirectStandardError "logs\$svc-$i.err"
        Set-Content -Path "logs\$svc-$i.pid" -Value $p.Id
        $started += [pscustomobject]@{ Service = $svc; Replica = $i; PID = $p.Id; Url = "${scheme}://localhost:$($services[$svc] + 100 * ($i - 1))" }
    }
}
Write-Host "$($started.Count) replicas starting ($Memory, profiles instanceN$(if ($Profiles) { ",$Profiles" }))."

function Get-HttpCode([string]$url) {
    # curl.exe (Windows 10+): -k accepts the development certificate of the tls profile
    return (& curl.exe -s -k -o NUL -w "%{http_code}" --max-time 2 $url)
}

# Liveness probe; services that do not expose the probe groups (404) fall back to /actuator/health.
function Get-Liveness([string]$url) {
    $code = Get-HttpCode "$url/actuator/health/liveness"
    if ($code -eq "404") { $code = Get-HttpCode "$url/actuator/health" }
    if ($code -eq "200") { return "UP" } else { return "DOWN" }
}

if ($WaitSeconds -gt 0) {
    Write-Host "Waiting for /actuator/health/liveness (up to ${WaitSeconds}s)..."
    $deadline = (Get-Date).AddSeconds($WaitSeconds)
    do {
        Start-Sleep -Seconds 5
        foreach ($s in $started) {
            $alive = [bool](Get-Process -Id $s.PID -ErrorAction SilentlyContinue)
            $s | Add-Member -Force Liveness $(if (-not $alive) { "EXITED" } else { Get-Liveness $s.Url })
        }
        $pending = @($started | Where-Object { $_.Liveness -eq "DOWN" })
    } while ($pending.Count -gt 0 -and (Get-Date) -lt $deadline)
}

$started | Format-Table Service, Replica, PID, Url, Liveness -AutoSize | Out-String | Write-Host
Write-Host "Swagger: <Url>/swagger-ui.html   Readiness (incl. RabbitMQ): <Url>/actuator/health/readiness"
Write-Host "Logs: code\logs\<service>-<n>.out   Stop: .\scripts\stop-all.ps1"
$failed = @($started | Where-Object { $_.Liveness -and $_.Liveness -ne "UP" })
if ($failed) {
    Write-Host "Replicas not live: $(($failed | ForEach-Object { "$($_.Service)-$($_.Replica)" }) -join ', '). See the .out files in code\logs\." -ForegroundColor Yellow
}
