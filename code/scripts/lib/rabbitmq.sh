# RabbitMQ helpers shared by start-all.sh and stop-all.sh (source: . scripts/lib/rabbitmq.sh, from code/).
# Broker definition: infra/docker-compose.rabbitmq.yml; credentials: infra/.env (copy of infra/.env.example).
# Conventions: docs/Messaging-Conventions.md

BROKER_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
BROKER_COMPOSE_FILE="$BROKER_REPO_ROOT/infra/docker-compose.rabbitmq.yml"
BROKER_ENV_FILE="$BROKER_REPO_ROOT/infra/.env"

# Exports the variables of infra/.env (inherited by every replica started afterwards).
# Variables already set in the environment win over the file. Returns 1 if the file is missing.
import_broker_env() {
  [ -f "$BROKER_ENV_FILE" ] || return 1
  local line name value
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    case "$line" in ''|'#'*) continue ;; *=*) ;; *) continue ;; esac
    name="${line%%=*}"; name="${name// /}"
    value="${line#*=}"
    [ -n "${!name:-}" ] || export "$name=$value"
  done < "$BROKER_ENV_FILE"
  return 0
}

# Succeeds if a TCP connection to host:port opens within 2 seconds (never hangs).
tcp_port_open() {
  if command -v timeout >/dev/null 2>&1; then
    timeout 2 bash -c "exec 3<>/dev/tcp/$1/$2" 2>/dev/null
  elif command -v nc >/dev/null 2>&1; then
    nc -z -w 2 "$1" "$2" 2>/dev/null
  else
    bash -c "exec 3<>/dev/tcp/$1/$2" 2>/dev/null
  fi
}

broker_error() {
  for l in "$@"; do echo "$l" >&2; done
  exit 1
}

# Makes sure RabbitMQ is reachable before the services start: if it is not, starts it with docker compose
# and waits (bounded) for it. Exits with a clear message instead of hanging.
# AISAFE_MESSAGING_ENABLED=false skips the check (the services then run without messaging).
assert_broker() {
  local timeout_s="${1:-90}" has_env=0
  if [ "${AISAFE_MESSAGING_ENABLED:-}" = "false" ]; then
    echo "AISAFE_MESSAGING_ENABLED=false: RabbitMQ not checked; services start without messaging."
    return 0
  fi
  import_broker_env && has_env=1
  if [ -z "${RABBITMQ_USER:-}" ] || [ -z "${RABBITMQ_PASSWORD:-}" ]; then
    broker_error "RabbitMQ credentials are not set (RABBITMQ_USER / RABBITMQ_PASSWORD)." \
      "Copy infra/.env.example to infra/.env (repository root) and set your own values," \
      "or set AISAFE_MESSAGING_ENABLED=false to start the services without messaging."
  fi
  local host="${RABBITMQ_HOST:-localhost}" port="${RABBITMQ_PORT:-5672}"
  if tcp_port_open "$host" "$port"; then
    echo "RabbitMQ is reachable at $host:$port (management UI: http://$host:15672)."
    return 0
  fi

  echo "RabbitMQ is not reachable at $host:$port; starting it with docker compose..."
  command -v docker >/dev/null 2>&1 || broker_error \
    "Docker is not installed (or not on PATH), so RabbitMQ cannot be started automatically." \
    "Start a broker on $host:$port yourself, or install Docker and run (repository root):" \
    "  docker compose -f infra/docker-compose.rabbitmq.yml --env-file infra/.env up -d --wait"
  [ "$has_env" -eq 1 ] || broker_error \
    "infra/.env is missing: copy infra/.env.example to infra/.env and set your own credentials."
  docker compose -f "$BROKER_COMPOSE_FILE" --env-file "$BROKER_ENV_FILE" up -d --wait || broker_error \
    "docker compose could not start RabbitMQ. Is the Docker daemon running?" \
    "Details: docker compose -f infra/docker-compose.rabbitmq.yml --env-file infra/.env logs rabbitmq"
  local deadline=$((SECONDS + timeout_s))
  until tcp_port_open "$host" "$port"; do
    [ "$SECONDS" -lt "$deadline" ] || broker_error "RabbitMQ did not accept connections on $host:$port within ${timeout_s}s."
    sleep 2
  done
  echo "RabbitMQ started (management UI: http://$host:15672)."
}

# Stops the broker container started from infra/docker-compose.rabbitmq.yml (the data volume is kept).
stop_broker() {
  command -v docker >/dev/null 2>&1 || { echo "Docker not found: broker not stopped."; return 0; }
  local env_args=()
  [ -f "$BROKER_ENV_FILE" ] && env_args=(--env-file "$BROKER_ENV_FILE")
  if docker compose -f "$BROKER_COMPOSE_FILE" "${env_args[@]}" down; then
    echo "RabbitMQ stopped (volume kept; erase it with: docker compose ... down -v)."
  else
    echo "docker compose down failed." >&2
  fi
}
