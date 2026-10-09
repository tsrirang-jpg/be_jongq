# Jongq backend

Spring Boot 4.1.1 + Java 17 + PostgreSQL, with JDBC repositories, Flyway migrations, and Spring Security.

## Run on this Windows machine

PostgreSQL 18 is already installed. Start an isolated persistent local database and the API without touching the existing PostgreSQL service:

```powershell
cd C:\Project\Springboot\jongq
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Start-Local.ps1
```

The script creates `.local-postgres/data`, binds its database to `127.0.0.1:5433`, creates `jongq`, and starts the API on `8080`. On shutdown it stops only the database instance that it started. Data persists between runs. If PostgreSQL is elsewhere, pass `-PostgresBin 'path\to\bin'`. For a different database port pass `-DatabasePort 5434`.

Local development login is `admin` / `admin123`. The dev profile supplies this bootstrap password only. Set `ADMIN_PASSWORD` before first startup to override it. An existing admin account is not overwritten on restart.

In another terminal start the frontend:

```powershell
cd C:\Project\ReactJS\my-app
npm.cmd run dev
```

Open `http://localhost:5173/` for customer bookings and `http://localhost:5173/admin` for admin login. Vite proxies `/api` to the backend.

## Neon database with a local backend

Create a Neon project and open **Connect**. Turn connection pooling off and copy the
direct host, database name, username, and password. This setup uses a direct connection
for both JDBC and Flyway. See [Neon's Java connection guide](https://neon.com/docs/guides/java).

Copy `.neon.example.properties` to `.neon.properties` and fill in all `REPLACE_*`
values. Use a JDBC URL such as `jdbc:postgresql://your-host.neon.tech:5432/neondb?sslmode=require`.
Keep the password in `DB_PASSWORD`; do not embed it in the URL. The config file is
excluded from Git. Java properties files require literal backslashes to be doubled.

After building the JAR, stop any backend already using port 8080 and run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Start-Neon.ps1
```

The `neon` profile requires explicit database and initial admin credentials. Do not
combine it with the `dev` profile or use `Start-Local.ps1`, which sets up local PostgreSQL.
Flyway creates the schema in Neon, and the bootstrap process creates the first admin.
Existing local bookings and accounts are not copied to Neon. Integration tests still
use the isolated local `jongq_test` database.

Check `/api/health` for process availability. To confirm the Neon database connection,
inspect the successful Flyway/Hikari startup messages and the `admins`, `bookings`,
and `flyway_schema_history` tables in the Neon SQL Editor or DBeaver.

## Docker database alternative

Use this instead of Start-Local, and ensure Docker Desktop is running:

```powershell
docker compose up -d db
$env:DB_URL = 'jdbc:postgresql://localhost:5433/jongq'
$env:DB_USERNAME = 'jongq'
$env:DB_PASSWORD = 'jongq-local-password'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
```

Compose defaults to port 5433 to avoid the PostgreSQL service already using 5432. Set `DB_PORT` and `DB_PASSWORD` in `.env` for Compose. Spring Boot reads process environment variables, not `.env` automatically. The Docker volume persists the database.

## Existing PostgreSQL / deployment

### Render with Neon

The repository includes `Dockerfile` and `render.yaml` for a Docker Web Service.
Push this backend to your Git repository, then create a Render **Blueprint** from it,
or create a **Web Service** with the Docker runtime and health check `/api/health`.
If this backend is in a monorepo, set its directory as the service Root Directory.
See [Render Docker deployment](https://render.com/docs/docker).

Set `SPRING_PROFILES_ACTIVE=neon,render` and supply `DB_URL`, `DB_USERNAME`,
`DB_PASSWORD`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`, and `FRONTEND_ORIGINS` in Render's
Environment settings. Use the Neon values in your local `.neon.properties`; the
Docker build excludes that file. `FRONTEND_ORIGINS` must contain exact allowed
frontend origins, separated by commas. Render provides `PORT` automatically.
The `render` profile enables HTTPS session cookies and forwarded header support.

The existing frontend uses relative `/api` URLs and SameSite=Lax session cookies.
For the deployed frontend, proxy `/api` to the Render backend from the frontend's
own HTTPS origin. Use the local Neon backend for local admin development.
Swagger can test the hosted API directly over HTTPS.

The health endpoint checks the application process. Bookings and admins persist
in Neon; restarting or redeploying the backend invalidates in-memory login sessions.
The default Blueprint selects Render's Free plan; review its current limitations
before creating the service. Deployment has not been performed automatically.

Create an empty `jongq` database with a dedicated owner, then configure `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `ADMIN_USERNAME`, and `ADMIN_PASSWORD`. Start without the dev profile:

```powershell
.\mvnw.cmd package -DskipTests
java -jar target/jongq-0.0.1-SNAPSHOT.jar
```

Flyway creates tables and upgrades schema on startup. The bootstrap admin is created only when there are no admins; its password is stored as a BCrypt hash. Set a private initial password of 8-72 ASCII characters. There is no default production password. Set `COOKIE_SECURE=true` behind HTTPS and configure `FRONTEND_ORIGINS` with exact permitted origins. Production should proxy `/api` from the same origin as the frontend. Serve the frontend's `index.html` for `/` and `/admin`.

## API

Swagger UI: http://localhost:8080/swagger-ui.html (after starting the backend).
OpenAPI JSON: http://localhost:8080/v3/api-docs; YAML: http://localhost:8080/v3/api-docs.yaml.

Use **Try it out** to call APIs. For admin operations, execute `POST /api/auth/login` first;
the browser retains the session cookie. Swagger UI fetches a fresh CSRF token automatically
before POST/PATCH/DELETE requests, including after login. Documentation is publicly accessible;
API authentication and CSRF protection still apply.

All responses are JSON except successful logout/delete (204). Errors return `{ "message": "..." }`.

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| GET | `/api/health` | Public | Process health |
| GET | `/api/auth/csrf` | Public | CSRF token and header name |
| POST | `/api/auth/login` | Public + CSRF | Authenticate admin |
| GET | `/api/auth/me` | Admin | Current session |
| POST | `/api/auth/logout` | CSRF | Invalidate session |
| GET | `/api/booking-events` | Public | Live booking-change stream (SSE) |
| GET | `/api/slots?date=YYYY-MM-DD` | Public | Time availability only |
| POST | `/api/bookings` | Public + CSRF | Create a booking |
| GET | `/api/bookings?date=YYYY-MM-DD` | Admin | Customer booking list |
| PATCH | `/api/bookings/{id}/status` | Admin + CSRF | Update status |
| DELETE | `/api/bookings/{id}` | Admin + CSRF | Delete and release the slot |

Dates default to today in Asia/Bangkok. Public endpoints do not disclose customer lists. Login body: `{ "username": "admin", "password": "..." }`. Booking body: `{ "name": "Customer", "phone": "0812345678", "date": "YYYY-MM-DD", "time": "10:00" }`; date is optional. Status body: `{ "status": "waiting" }`, with `waiting`, `cutting`, or `done`.

Sessions use an HttpOnly SameSite=Lax cookie. Include credentials and fetch a CSRF token before POST/PATCH/DELETE; send its `headerName` and `token`. Login rotates the session ID and CSRF token; fetch a new token after login. Sessions expire after eight hours of inactivity and are invalidated on logout. Sessions are currently held by the server process, so a backend restart signs admins out; bookings and accounts persist in PostgreSQL.

Status codes: 400 invalid input, 401 signed out or invalid credentials, 403 missing CSRF/role, 404 missing booking, 409 occupied slot, 429 repeated failed logins, 503 database unavailable. Database uniqueness on `(booking_date, booking_time)` arbitrates concurrent requests.

## Database and layers

- `admins`: UUID string ID, unique username, password hash, creation timestamp.
- `bookings`: UUID string ID, customer name/phone, date/time, status, creation timestamp; unique date/time and status/phone/time constraints.
- `config/SecurityConfig`: server authentication/authorization, CORS, CSRF.
- `auth`: login, session lookup, admin bootstrap/user lookup.
- `booking`: controller DTOs, service rules, SQL repository.
- `common`: consistent API exceptions and error responses.
- `src/main/resources/db/migration`: versioned schema migrations.

The database starts with no bookings; old browser localStorage demo data is not imported automatically.

## Integration tests

Tests must use a separate database named `jongq_test`; tests clear booking rows in that database. Create it first, then run:

```powershell
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5433/jongq_test'
$env:TEST_DB_USERNAME = 'jongq'
$env:TEST_DB_PASSWORD = 'your-test-database-password'
.\mvnw.cmd test
```

Tests use the `test` profile, ignoring production `DB_URL`. They cover login/session/logout, real CSRF and session rotation, admin authorization, public privacy, validation, booking CRUD, date isolation, and simultaneous booking uniqueness.
Login throttling allows five failed attempts per IP per minute in each server process; configure a shared limiter at the gateway when running multiple instances.

Framework references: [Spring Security session persistence](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html) and [Spring Boot Flyway database initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html).

## Live booking notifications

`GET /api/booking-events` opens a persistent `text/event-stream` response. The initial `connected` event flushes the stream. After a successful create, status change, or delete commits in PostgreSQL, an AFTER_COMMIT transactional listener sends:

```text
event:booking-changed
data:{"date":"2026-10-08"}
```

No customer name, phone number, or booking ID is broadcast. Failed or rolled-back transactions and status updates to the same value send no change event. A comment heartbeat every 25 seconds keeps proxy connections alive without reading the database. Completed, errored, or disconnected emitters are removed.

Clients reload on connection/reconnection, so changes missed during a disconnect are recovered. A production reverse proxy must support streaming and disable response buffering for this endpoint (the response also sets `X-Accel-Buffering: no`). EventSource retries after five seconds when the connection breaks. This implementation broadcasts within one backend process; multiple backend instances require a shared event transport, such as PostgreSQL LISTEN/NOTIFY or Redis pub/sub. Changes made directly in SQL do not emit application events.

Restart the running backend to activate the SSE endpoint. On Windows a running JAR can lock its build artifact; stop that process before packaging its replacement, or use `spring-boot:run` from source.

Tests also verify that committed writes notify clients, rolled-back writes do not, and the stream contains no customer information. Frontend tests verify idle/focus inactivity, burst coalescing, shared connections, local-write deduplication, reconnection, cross-tab logout, and disposal.
