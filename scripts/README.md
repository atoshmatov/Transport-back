# scripts/ — local dev helper scripts

This folder holds small helper scripts for running the backend locally.
Currently: `wait-for-postgres.ps1` / `wait-for-postgres.sh`, which exist to
solve one recurring local-dev problem — see below.

## Problem: app starts before Postgres is ready (startup race condition)

`docker compose up -d` returns **immediately**, before the containers it
started are actually ready to accept connections. Postgres in particular
takes a few seconds to initialize on a cold start. If `./gradlew bootRun`
(or the IDE's Run button) fires right after `docker compose up -d`, Spring
Boot tries to open a DB connection while Postgres is still starting, and the
whole `ApplicationContext` fails to come up:

```
org.postgresql.util.PSQLException: Connection refused (localhost:5433)
```

This isn't a one-time fluke — it can happen any time the containers aren't
already warm before the app starts, for example:

- After a machine or Docker Desktop restart, then immediately pressing Run
  in the IDE.
- Running `docker compose up -d` and `./gradlew bootRun` back-to-back in two
  terminals/scripts without waiting in between.
- CI or an onboarding script that starts infra and the app in one go.

## Solution: `wait-for-postgres`

Instead of calling `docker compose up -d` directly, use one of these
scripts. Both do the same thing:

1. Run `docker compose up -d` (safe to call even if containers are already
   up — idempotent).
2. Poll the `transport-observer-postgres` container's Docker healthcheck
   (already defined in `docker-compose.yml`, backed by `pg_isready`) until
   it reports `healthy`, or fail with a clear timeout error after 60s.
3. Optionally start `./gradlew bootRun` automatically once Postgres is
   confirmed healthy, if you pass the run flag.

### Usage

```powershell
# PowerShell
.\scripts\wait-for-postgres.ps1          # wait for Postgres, then Run from the IDE yourself
.\scripts\wait-for-postgres.ps1 -Run     # wait for Postgres, then also run ./gradlew bootRun
```

```bash
# Git Bash / WSL
./scripts/wait-for-postgres.sh           # wait for Postgres, then Run from the IDE yourself
./scripts/wait-for-postgres.sh --run     # wait for Postgres, then also run ./gradlew bootRun
```

If it times out, check `docker compose logs postgres` and make sure Docker
Desktop is actually running.

See the main [README.md](../README.md#running-locally) for full local-dev
setup steps (ports, env vars, bootstrap admin account, etc.).

## Unrelated but related annoyance: "port 8082 was already in use"

This is a separate problem from the race condition above, but shows up
around the same workflow so it's worth noting here too.

**Cause**: a previous `bootRun` (or IDE run) didn't get stopped cleanly —
its `java.exe` process is still alive and still holding port `8082`, so the
next `bootRun` fails immediately with a "port already in use" error before
it even tries to reach the database.

**Fix**: find and stop the process holding the port.

```powershell
# PowerShell — find the PID bound to 8082
Get-NetTCPConnection -LocalPort 8082 | Select-Object -Property OwningProcess

# Then stop it (replace <pid> with the OwningProcess value above)
Stop-Process -Id <pid> -Force
```

```bash
# Git Bash
netstat -ano | grep ':8082'
# note the PID in the last column, then:
taskkill //PID <pid> //F
```

This is a one-off manual fix, not something a script needs to automate —
it only happens after an unclean shutdown of a previous run.
