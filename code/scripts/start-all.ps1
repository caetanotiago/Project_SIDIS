# Arranca os 4 microsserviços (N réplicas de cada) em background, cada réplica com run-instance.ps1.
# Uso (a partir de code\):
#   .\scripts\start-all.ps1                               # 3 réplicas de cada serviço (12 JVMs)
#   .\scripts\start-all.ps1 -Replicas 1                   # 1 réplica de cada (máquinas com pouca memória)
#   .\scripts\start-all.ps1 -Profiles demo -Memory "-Xmx256m"   # (com aspas: começa por "-")
#   .\scripts\start-all.ps1 -Profiles demo,tls            # HTTPS (gerar antes: .\scripts\generate-dev-certs.ps1)
# Logs e PIDs em code\logs\ (ignorado pelo git): <servico>-<n>.out/.err (consola) e <servico>-<n>.pid.
# Parar tudo: .\scripts\stop-all.ps1
param(
    [ValidateRange(1, 3)][int]$Replicas = 3,
    [string]$Profiles = "",
    [string]$Memory = "-Xmx320m",
    [int]$WaitSeconds = 150
)
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "lib\dev-certs.ps1")
$codeDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $codeDir

# Porta base de cada serviço; a réplica n usa base + 100*(n-1) (docs/2-containers/Ports.md)
$services = [ordered]@{ "aircraftmanagement" = 8081; "maintenance" = 8082; "airports" = 8083; "flightroutes" = 8084 }

Assert-DevCerts $Profiles
$outdated = $services.Keys | Where-Object { Test-JarOutdated "$_\target\$_-0.0.1-SNAPSHOT.jar" $Profiles }
if ($outdated) {
    Write-Host "A compilar (JARs em falta ou mais antigos do que os certificados: $($outdated -join ', '))..."
    .\mvnw.cmd -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "A compilacao falhou." }
}

New-Item -ItemType Directory -Force "logs" | Out-Null
$running = Get-ChildItem "logs\*.pid" -ErrorAction SilentlyContinue |
    Where-Object { Get-Process -Id ([int](Get-Content $_.FullName)) -ErrorAction SilentlyContinue }
if ($running) {
    throw "Ja ha replicas a correr ($($running.BaseName -join ', ')). Parar primeiro com .\scripts\stop-all.ps1"
}

$scheme = if (Test-TlsProfile $Profiles) { "https" } else { "http" }
$runInstance = Join-Path $PSScriptRoot "run-instance.ps1"
$env:JAVA_OPTS = $Memory   # herdado pelos processos filhos e lido por run-instance.ps1
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
Write-Host "$($started.Count) replicas a arrancar ($Memory, perfis instanceN$(if ($Profiles) { ",$Profiles" }))."

function Get-Health([string]$url) {
    # curl.exe (Windows 10+): -k aceita o certificado de desenvolvimento no perfil tls
    $code = & curl.exe -s -k -o NUL -w "%{http_code}" --max-time 2 "$url/actuator/health"
    if ($code -eq "200") { return "UP" } else { return "DOWN" }
}

if ($WaitSeconds -gt 0) {
    Write-Host "A aguardar /actuator/health (ate ${WaitSeconds}s)..."
    $deadline = (Get-Date).AddSeconds($WaitSeconds)
    do {
        Start-Sleep -Seconds 5
        foreach ($s in $started) {
            $alive = [bool](Get-Process -Id $s.PID -ErrorAction SilentlyContinue)
            $s | Add-Member -Force Health $(if (-not $alive) { "EXITED" } else { Get-Health $s.Url })
        }
        $pending = @($started | Where-Object { $_.Health -eq "DOWN" })
    } while ($pending.Count -gt 0 -and (Get-Date) -lt $deadline)
}

$started | Format-Table Service, Replica, PID, Url, Health -AutoSize | Out-String | Write-Host
Write-Host "Swagger: <Url>/swagger-ui.html   Logs: code\logs\<servico>-<n>.out   Parar: .\scripts\stop-all.ps1"
$failed = @($started | Where-Object { $_.Health -and $_.Health -ne "UP" })
if ($failed) {
    Write-Host "Replicas sem health UP: $(($failed | ForEach-Object { "$($_.Service)-$($_.Replica)" }) -join ', '). Ver os .out em code\logs\." -ForegroundColor Yellow
}
