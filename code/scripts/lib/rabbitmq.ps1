# RabbitMQ helpers shared by start-all.ps1 and stop-all.ps1 (dot-source: . "$PSScriptRoot\lib\rabbitmq.ps1").
# Broker definition: infra\docker-compose.rabbitmq.yml; credentials: infra\.env (copy of infra\.env.example).
# Conventions: docs\Messaging-Conventions.md

$script:RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$script:BrokerComposeFile = Join-Path $script:RepoRoot "infra\docker-compose.rabbitmq.yml"
$script:BrokerEnvFile = Join-Path $script:RepoRoot "infra\.env"

# Loads infra\.env into the environment of this process (inherited by every replica started afterwards).
# Variables that are already set in the environment win over the file. Returns $false if the file is missing.
function Import-BrokerEnv {
    if (-not (Test-Path $script:BrokerEnvFile)) { return $false }
    foreach ($line in Get-Content $script:BrokerEnvFile) {
        $t = $line.Trim()
        if (-not $t -or $t.StartsWith("#") -or -not $t.Contains("=")) { continue }
        $parts = $t.Split("=", 2)
        $name = $parts[0].Trim()
        if (-not [Environment]::GetEnvironmentVariable($name)) { Set-Item -Path "env:$name" -Value $parts[1].Trim() }
    }
    return $true
}

# $true if a TCP connection to host:port succeeds within the timeout (never hangs).
function Test-TcpPort([string]$HostName, [int]$Port, [int]$TimeoutMs = 2000) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $task = $client.ConnectAsync($HostName, $Port)
        return ($task.Wait($TimeoutMs) -and $client.Connected)
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Get-BrokerHost { if ($env:RABBITMQ_HOST) { $env:RABBITMQ_HOST } else { "localhost" } }
function Get-BrokerPort { if ($env:RABBITMQ_PORT) { [int]$env:RABBITMQ_PORT } else { 5672 } }

function Stop-WithBrokerError([string[]]$Lines) {
    foreach ($l in $Lines) { Write-Host $l -ForegroundColor Red }
    throw "RabbitMQ is not available."
}

# Makes sure RabbitMQ is reachable before the services start: if it is not, starts it with docker compose
# and waits (bounded) for it. Fails with a clear message instead of hanging.
# AISAFE_MESSAGING_ENABLED=false skips the check (the services then run without messaging).
function Assert-Broker([int]$TimeoutSeconds = 90) {
    if ($env:AISAFE_MESSAGING_ENABLED -eq "false") {
        Write-Host "AISAFE_MESSAGING_ENABLED=false: RabbitMQ not checked; services start without messaging." -ForegroundColor Yellow
        return
    }
    $hasEnvFile = Import-BrokerEnv
    if (-not $env:RABBITMQ_USER -or -not $env:RABBITMQ_PASSWORD) {
        Stop-WithBrokerError @(
            "RabbitMQ credentials are not set (RABBITMQ_USER / RABBITMQ_PASSWORD).",
            "Copy infra\.env.example to infra\.env (repository root) and set your own values,",
            "or set AISAFE_MESSAGING_ENABLED=false to start the services without messaging.")
    }
    $brokerHost = Get-BrokerHost
    $brokerPort = Get-BrokerPort
    if (Test-TcpPort $brokerHost $brokerPort) {
        Write-Host "RabbitMQ is reachable at ${brokerHost}:$brokerPort (management UI: http://${brokerHost}:15672)."
        return
    }

    Write-Host "RabbitMQ is not reachable at ${brokerHost}:$brokerPort; starting it with docker compose..."
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Stop-WithBrokerError @(
            "Docker is not installed (or not on PATH), so RabbitMQ cannot be started automatically.",
            "Start a broker on ${brokerHost}:$brokerPort yourself, or install Docker and run (repository root):",
            "  docker compose -f infra/docker-compose.rabbitmq.yml --env-file infra/.env up -d --wait")
    }
    if (-not $hasEnvFile) {
        Stop-WithBrokerError @("infra\.env is missing: copy infra\.env.example to infra\.env and set your own credentials.")
    }
    & docker compose -f $script:BrokerComposeFile --env-file $script:BrokerEnvFile up -d --wait
    if ($LASTEXITCODE -ne 0) {
        Stop-WithBrokerError @(
            "docker compose could not start RabbitMQ (exit code $LASTEXITCODE). Is Docker Desktop running?",
            "Details: docker compose -f infra/docker-compose.rabbitmq.yml --env-file infra/.env logs rabbitmq")
    }
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while (-not (Test-TcpPort $brokerHost $brokerPort)) {
        if ((Get-Date) -gt $deadline) {
            Stop-WithBrokerError @("RabbitMQ did not accept connections on ${brokerHost}:$brokerPort within ${TimeoutSeconds}s.")
        }
        Start-Sleep -Seconds 2
    }
    Write-Host "RabbitMQ started (management UI: http://${brokerHost}:15672)."
}

# Stops the broker container started from infra\docker-compose.rabbitmq.yml (the data volume is kept).
function Stop-Broker {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Write-Host "Docker not found: broker not stopped."; return }
    $envArgs = @()
    if (Test-Path $script:BrokerEnvFile) { $envArgs = @("--env-file", $script:BrokerEnvFile) }
    & docker compose -f $script:BrokerComposeFile @envArgs down
    if ($LASTEXITCODE -eq 0) { Write-Host "RabbitMQ stopped (volume kept; erase it with: docker compose ... down -v)." }
    else { Write-Host "docker compose down failed (exit code $LASTEXITCODE)." -ForegroundColor Yellow }
}
