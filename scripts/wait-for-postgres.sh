#!/usr/bin/env bash
#
# Brings up the local docker-compose infrastructure and waits for Postgres
# to report "healthy" before returning control. Git Bash / Unix-shell
# counterpart of scripts/wait-for-postgres.ps1 — same behavior, use
# whichever shell you have open.
#
# Prevents the classic race condition where `./gradlew bootRun` is started
# while `transport-observer-postgres` is still initializing in the
# background — Spring Boot then fails to build the ApplicationContext with
# `org.postgresql.util.PSQLException: Connection refused (localhost:5433)`.
#
# Usage:
#   ./scripts/wait-for-postgres.sh          # start infra, wait, then exit
#   ./scripts/wait-for-postgres.sh --run    # also run ./gradlew bootRun after

set -euo pipefail

# Must match `container_name` for the postgres service in docker-compose.yml.
CONTAINER_NAME="transport-observer-postgres"
POLL_INTERVAL_SECONDS=2
TIMEOUT_SECONDS=60

# Resolve repo root (this script lives in <repo>/scripts) so it works
# regardless of the caller's current directory.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

RUN_AFTER=false
if [[ "${1:-}" == "--run" ]]; then
    RUN_AFTER=true
fi

echo "==> docker compose up -d"
docker compose up -d

echo "==> Waiting for '$CONTAINER_NAME' to become healthy (timeout: ${TIMEOUT_SECONDS}s)..."
elapsed=0
status="unknown"
while true; do
    # Suppress docker's own stderr noise (e.g. right after container
    # creation) - just keep polling until timeout instead.
    status="$(docker inspect --format='{{.State.Health.Status}}' "$CONTAINER_NAME" 2>/dev/null || echo "unknown")"

    if [[ "$status" == "healthy" ]]; then
        echo "==> '$CONTAINER_NAME' is healthy."
        break
    fi

    if [[ "$elapsed" -ge "$TIMEOUT_SECONDS" ]]; then
        echo "ERROR: Timed out after ${TIMEOUT_SECONDS}s waiting for '$CONTAINER_NAME' to become healthy (last status: '$status'). Check 'docker compose logs postgres'." >&2
        exit 1
    fi

    sleep "$POLL_INTERVAL_SECONDS"
    elapsed=$((elapsed + POLL_INTERVAL_SECONDS))
done

echo "==> Postgres is ready on localhost:5433. Safe to start the app now."

if [[ "$RUN_AFTER" == true ]]; then
    echo "==> ./gradlew bootRun"
    ./gradlew bootRun
fi
