<#
.SYNOPSIS
    Brings up the local docker-compose infrastructure and waits for
    Postgres to report "healthy" before returning control.

.DESCRIPTION
    Prevents the classic race condition where `./gradlew bootRun` (or an
    IDE Run button) is started while `transport-observer-postgres` is still
    initializing in the background — Spring Boot then fails to build the
    ApplicationContext with
    `org.postgresql.util.PSQLException: Connection refused (localhost:5433)`.

    This script:
      1. Runs `docker compose up -d` (idempotent — safe even if the
         containers are already up).
      2. Polls `docker inspect` for the postgres container's healthcheck
         status (already defined in docker-compose.yml via `pg_isready`)
         until it reports "healthy", or exits with an error after a
         timeout.
      3. Optionally launches `./gradlew bootRun` afterwards if -Run is
         passed.

.PARAMETER Run
    After Postgres is healthy, also start `./gradlew bootRun` automatically.
    Without this switch, the script just prints readiness and exits 0 —
    useful if you prefer to press Run in IntelliJ yourself.

.EXAMPLE
    ./scripts/wait-for-postgres.ps1
    # Starts infra, waits for postgres, then you press Run in the IDE.

.EXAMPLE
    ./scripts/wait-for-postgres.ps1 -Run
    # Starts infra, waits for postgres, then runs the app for you.
#>

param(
    [switch]$Run
)

$ErrorActionPreference = "Stop"

# Must match `container_name` for the postgres service in docker-compose.yml.
$ContainerName = "transport-observer-postgres"
$PollIntervalSeconds = 2
$TimeoutSeconds = 60

# Resolve repo root (this script lives in <repo>/scripts) so it works
# regardless of the caller's current directory.
$RepoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $RepoRoot
try {
    Write-Host "==> docker compose up -d"
    docker compose up -d
    if ($LASTEXITCODE -ne 0) {
        Write-Error "docker compose up failed (exit code $LASTEXITCODE). Is Docker Desktop running?"
        exit 1
    }

    Write-Host "==> Waiting for '$ContainerName' to become healthy (timeout: ${TimeoutSeconds}s)..."
    $elapsed = 0
    $status = "unknown"
    while ($true) {
        # Suppress docker's own stderr noise (e.g. "No such container" right
        # after creation) - we just keep polling until timeout instead.
        $status = docker inspect --format='{{.State.Health.Status}}' $ContainerName 2>$null
        if ($status -eq "healthy") {
            Write-Host "==> '$ContainerName' is healthy."
            break
        }

        if ($elapsed -ge $TimeoutSeconds) {
            Write-Error "Timed out after ${TimeoutSeconds}s waiting for '$ContainerName' to become healthy (last status: '$status'). Check 'docker compose logs postgres'."
            exit 1
        }

        Start-Sleep -Seconds $PollIntervalSeconds
        $elapsed += $PollIntervalSeconds
    }

    Write-Host "==> Postgres is ready on localhost:5433. Safe to start the app now."

    if ($Run) {
        Write-Host "==> ./gradlew bootRun"
        & "$RepoRoot\gradlew.bat" bootRun
        exit $LASTEXITCODE
    }
}
finally {
    Pop-Location
}
