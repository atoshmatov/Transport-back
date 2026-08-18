# Transport Observer — Backend

Spring Boot (Kotlin) backend for the Transport Observer platform. Modular
monolith: one deployable app, domains kept in separate packages with their
own entity/repository/service/controller layers.

Status: **skeleton / MVP scaffold**. `auth` is the most complete module
(login, refresh, logout, change-password, reset-password, JWT + RBAC,
lockout). Every other module (`employees`, `incidents`, `map`, `reports`,
`railsafe`, `notifications`, `audit`) has a minimal entity + read-only
endpoint so the shape of the system is in place; business logic is not yet
implemented — see the `TODO` comments in each module's `service` class.

## Stack

- Kotlin + Spring Boot 3.3, Gradle (Kotlin DSL)
- Spring Web, Spring Security (JWT), Spring Data JPA + Hibernate Spatial (PostGIS)
- Spring WebSocket (STOMP) for live map/notifications
- Spring AMQP (RabbitMQ) for cross-module async events
- Spring Data Redis for refresh-token storage/revocation
- PostgreSQL + PostGIS, MinIO (S3-compatible) for file storage

## Project layout

```
src/main/kotlin/uz/safecity/transportobserver/
├── TransportObserverApplication.kt
├── common/            shared BaseEntity, ApiResponse/ErrorResponse envelopes,
│                       GlobalExceptionHandler, Redis/RabbitMQ/WebSocket/MinIO config
├── auth/               accounts, JWT, RBAC, lockout — the MVP's core
│   ├── entity/          Account, RoleType
│   ├── security/        SecurityConfig, JwtService, JwtAuthenticationFilter,
│   │                     PasswordChangeRequiredFilter, RefreshTokenService (Redis)
│   ├── service/          AuthService
│   └── controller/       AuthController
├── employees/          xodimlar (skeleton)
├── incidents/          hodisalar, PostGIS Point location (skeleton)
├── map/                live vehicle/inspector locations (skeleton)
├── reports/            hisobotlar (skeleton)
├── railsafe/           temir yo'l kesishmasi xavfsizligi (skeleton)
├── notifications/      real-time bildirishnomalar (skeleton)
└── audit/              audit log (skeleton)
```

Each domain package follows the same internal shape:
`entity/ -> repository/ -> service/ -> controller/` (+ `dto/` where the
entity shouldn't be serialized directly).

## Auth flow (implemented)

- `POST /api/v1/auth/login` — username + password only, no OTP. Tracks
  `failed_attempts`; after `security.lockout.max-attempts` the account is
  locked for `security.lockout.lock-duration-minutes`.
- `POST /api/v1/auth/refresh` — rotates the opaque refresh token (stored in
  Redis, not a JWT, so it can be revoked instantly).
- `POST /api/v1/auth/logout` — revokes the given refresh token.
- `POST /api/v1/auth/change-password` — lets the account change its password
  at any time. `mustChangePassword = true` (e.g. right after an admin reset)
  is returned in the login response as a hint for the frontend to show an
  optional "you're on a temporary password" reminder, but it is **not**
  enforced server-side: the user can keep using every other endpoint and
  change the password later from Settings whenever they choose (product
  decision, 2026-08-12). Enforcement can be turned back on per-deployment via
  `app.security.enforce-password-change=true` (`ENFORCE_PASSWORD_CHANGE` env
  var) without code changes — see `PasswordChangeRequiredFilter`.
- `POST /api/v1/auth/reset-password` — ADMIN/SUPER_ADMIN only. Generates a
  temporary password, sets `mustChangePassword = true`, revokes all of that
  account's sessions.
- Accounts are never self-registered — only created by an admin (see
  `employees` module TODOs for the create-employee-with-account flow).

## Running locally

1. Start infrastructure (Docker Desktop must be running):

   ```bash
   docker compose up -d
   ```

   This brings up PostgreSQL+PostGIS (host `:5433` -> container `:5432`),
   Redis (`:6379`), RabbitMQ (`:5672`, management UI on `:15672`), and MinIO
   (`:9000`, console on `:9001`). The Spring Boot app itself is **not**
   containerized yet — run it on the host against these services.

   > **Why Postgres is on host port 5433, not 5432**: on this machine port
   > `5432` is already bound by a native Windows PostgreSQL service
   > (`postgresql-x64-18`, unrelated to this project). Mapping the container
   > to `5433` avoids a port clash with that service instead of touching it.
   > `application-dev.yml` already points at `5433` by default
   > (`DB_PORT=5433`), so no extra configuration is needed — just make sure
   > nothing else on your machine is also using `5433`.

   Check everything came up healthy:

   ```bash
   docker compose ps
   ```

   `transport-observer-postgres` should show `(healthy)`; the others just
   need to show `Up`.

   > **Avoid the startup race condition**: `docker compose up -d` returns
   > immediately, but Postgres takes a few seconds to actually become ready
   > — starting the app (via IDE Run or `./gradlew bootRun`) too early fails
   > with `PSQLException: Connection refused (localhost:5433)`. Use the
   > helper script instead of `docker compose up -d` to block until Postgres
   > is actually healthy:
   >
   > ```powershell
   > # PowerShell
   > .\scripts\wait-for-postgres.ps1          # wait, then Run from the IDE yourself
   > .\scripts\wait-for-postgres.ps1 -Run     # wait, then also run ./gradlew bootRun
   > ```
   >
   > ```bash
   > # Git Bash
   > ./scripts/wait-for-postgres.sh           # wait, then Run from the IDE yourself
   > ./scripts/wait-for-postgres.sh --run     # wait, then also run ./gradlew bootRun
   > ```
   >
   > Details on why this race condition happens (and the separate "port 8082
   > already in use" issue from a leftover process): see
   > [scripts/README.md](scripts/README.md).

2. Run the app from IntelliJ IDEA — open `TransportObserverApplication.kt`,
   set the run configuration's active profile to `dev` (or pass
   `-Dspring.profiles.active=dev`), and press Run. As long as step 1's
   containers are already up, no other setup is needed.

   Or from the command line (dev profile is active by default, see
   `application.yml` / `application-dev.yml`):

   ```bash
   ./gradlew bootRun
   ```

   The API listens on `http://localhost:8082`. Verify with:

   ```bash
   curl http://localhost:8082/actuator/health
   # {"status":"UP"}
   ```

### First login (dev only) — bootstrap admin account

Per the TZ, accounts are **never self-registered** — only an admin can create
one (see `employees` module TODOs). That's correct for production, but it
also means a brand-new database has an empty `accounts` table, so there is no
way to log in at all on a fresh checkout.

To break that chicken-and-egg problem, `DevBootstrapAdminSeeder`
(`auth/config/DevBootstrapAdminSeeder.kt`, `@Profile("dev")`) runs once on
startup and, **only if `accounts` is completely empty**, creates a single
bootstrap account:

| Username | Password      | Role        | Must change password on first login |
|----------|---------------|-------------|---------------------------------------|
| `admin`  | `Admin@12345` | SUPER_ADMIN | yes                                    |

Use it to log in once. `mustChangePassword` will be `true` on this account,
but calling `POST /api/v1/auth/change-password` is optional (not enforced —
see the "Auth flow" section above); use the real admin account from then on
to provision everyone else.

```bash
curl -X POST http://localhost:8082/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@12345"}'
```

**This seed runs only under the `dev` Spring profile.** There is no
equivalent for `prod`/any other profile, intentionally — a real deployment's
first admin must be provisioned manually/out-of-band, never by an auto-run
bean. If `accounts` already has any row (including one you added yourself),
the seeder does nothing.

3. Run tests:

   ```bash
   ./gradlew test
   ```

4. Stopping infrastructure:

   ```bash
   docker compose down       # stop containers, keep data
   docker compose down -v    # stop containers and wipe volumes (fresh start)
   ```

### Environment variables (all have dev-friendly defaults)

| Variable | Purpose |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASS` | PostgreSQL connection |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Redis connection |
| `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASS` | RabbitMQ connection |
| `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET` | MinIO/S3 |
| `JWT_SECRET`, `JWT_ACCESS_TTL_SECONDS`, `JWT_REFRESH_TTL_SECONDS` | JWT signing/lifetime — **set a real `JWT_SECRET` outside dev** |
| `LOGIN_MAX_ATTEMPTS`, `LOGIN_LOCK_DURATION_MINUTES` | Lockout policy |
| `ENFORCE_PASSWORD_CHANGE` | If `true`, blocks every endpoint except `/auth/**` with 403 while `mustChangePassword = true` (default `false` — change is optional, see "Auth flow" above) |

## Next steps (not done in this skeleton)

- `employees`: admin create/update flow that also provisions the linked
  `Account` (username/role), dismissal -> revoke sessions.
- `incidents`: full create/update/status-transition flow + RabbitMQ event
  publish for `notifications`.
- `map`: real-time location ingestion (WebSocket/mobile push) instead of
  reading a static table; bounding-box query for the viewport.
- `railsafe`: RabbitMQ consumer for sensor/CV events coming from the
  Safecity FastAPI side.
- `notifications`: `@RabbitListener` + STOMP push to `/user/queue/notifications`.
- `audit`: wire `AuditService.record(...)` into auth/employees/incidents
  mutations (or replace with an AOP aspect).
- Replace `spring.jpa.hibernate.ddl-auto: update` with Flyway/Liquibase
  migrations before this goes anywhere near production.
- Lock down CORS (`SecurityConfig.corsConfigurationSource`) to the real
  admin-panel and mobile origins.
- Lock down the WebSocket handshake origin (`WebSocketConfig.registerStompEndpoints`
  `setAllowedOriginPatterns("*")`) the same way — it's dev-only, same reason
  as CORS above. Note this is separate from STOMP auth: CONNECT-time JWT
  validation is already enforced by `WebSocketAuthChannelInterceptor`, this
  TODO is only about which browser origins may open the socket at all.
