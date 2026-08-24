# iVelox Backend — Architecture & Structure

## Overview
Java Spring Boot REST API — personal single-owner platform.
Auth is Telegram-OTP based: the owner requests an OTP, receives it via Telegram, verifies it, and gets a self-issued JWT back. No third-party auth provider (no Supabase JWT verification).
DB is PostgreSQL (Supabase) in production, H2 file DB for local dev. Schema managed by Flyway.

## Tech Stack
| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4 |
| Database | PostgreSQL (Supabase) in prod, H2 file DB locally |
| Migrations | Flyway |
| Auth | Self-issued JWT (jjwt), OTP delivered via Telegram bot |
| AI | Gemini (nutrition resolution for food logging) |
| Build | Maven (`mvnw`) |

## Package Layout

```
com.ivelox.core
├── auth/
│   ├── AuthController.java      # /api/v1/auth/otp/request, /otp/verify, /me
│   ├── OtpService.java          # generate/verify OTP, rate limiting
│   ├── JwtService.java          # issue/verify owner JWT
│   └── JwtAuthFilter.java       # Spring Security filter — reads Bearer JWT
│
├── config/
│   ├── IveloxProperties.java    # @ConfigurationProperties(prefix = "ivelox")
│   ├── SecurityConfig.java      # SecurityFilterChain, CORS
│   └── AppConfig.java           # misc bean wiring
│
├── health/                      # Food/meal/body/burn/goal tracking
│   ├── HealthController.java    # /api/v1/health/** endpoints
│   ├── HealthProfileService.java# body metrics, goals, burns, meal plan, today/weekly check
│   ├── FoodResolveService.java  # resolves free-text/image food input via Gemini
│   ├── MealService.java         # meal log create/list/delete
│   ├── BodyMath.java            # BMI/BMR/TDEE calculations
│   ├── HealthPlanning.java      # meal plan + weekly aggregation logic
│   ├── HealthModels.java        # request/response records
│   ├── CivilDay.java            # date parsing helper (YYYY-MM-DD)
│   ├── MealLogRepository.java
│   ├── BodyMetricsRepository.java
│   ├── BurnLogRepository.java
│   └── HealthGoalsRepository.java
│
├── platform/
│   └── PlatformController.java  # /api/v1/health (liveness), /api/v1/features
│
└── telegram/
    └── TelegramClient.java      # sends OTP messages via Telegram Bot API

src/main/resources/
├── application.yml              # Spring config, env var bindings
└── db/migration/
    ├── V1__food_cache_meal_logs.sql
    └── V2__body_burns_goals.sql
```

**Layering rule:** Controllers are thin (parse → call service → respond). SQL lives only in `*Repository` classes. Schema changes go through a new Flyway migration, never manual DDL.

## API Routes

```
# Public
GET  /api/v1/health                         # Liveness — {"status":"ok"}
GET  /api/v1/features                       # Feature flags (health.enabled, auth_required)
POST /api/v1/auth/otp/request               # Request OTP → sent via Telegram to owner
POST /api/v1/auth/otp/verify                # Verify OTP code → {access_token, token_type, expires_in}
GET  /actuator/health                       # Spring Boot actuator health
GET  /actuator/info                         # Spring Boot actuator info

# Protected (Bearer JWT required)
GET  /api/v1/auth/me                        # {subject, role}

POST /api/v1/health/foods/resolve           # Resolve food text/image → nutrition (Gemini)
POST /api/v1/health/meals                   # Create meal log
GET  /api/v1/health/meals?date=             # List meals for a date
DELETE /api/v1/health/meals/{id}            # Delete meal log

POST /api/v1/health/body-metrics            # Record body metric (weight, etc.)
GET  /api/v1/health/body-metrics/latest     # Latest body metric
GET  /api/v1/health/body-metrics?from=&to=  # Body metric history

PUT  /api/v1/health/goals                   # Upsert BMI/nutrition goals
GET  /api/v1/health/goals                   # Get current goals
GET  /api/v1/health/goals/meal-plan         # Generated meal plan from goals

POST /api/v1/health/burns                   # Log a calorie burn (exercise)
GET  /api/v1/health/burns?date=             # List burns for a date
DELETE /api/v1/health/burns/{id}            # Delete burn log

GET  /api/v1/health/check/today?date=       # Today's summary (intake vs goals)
GET  /api/v1/health/check/weekly?days=      # Weekly rollup (default 7 days)
```

All `/api/v1/health/**` routes 404 with `{"error":"health feature disabled"}` when `ivelox.health-enabled=false`.

## Auth Flow
```
1. Owner calls POST /api/v1/auth/otp/request (no auth)
2. OtpService generates a code, TelegramClient sends it to TELEGRAM_CHAT_ID via bot
3. Owner calls POST /api/v1/auth/otp/verify {code}
4. OtpService validates code (TTL + rate limit) → JwtService issues a JWT (subject = owner)
5. Client sends Authorization: Bearer <jwt> on subsequent requests
6. JwtAuthFilter validates the token → sets Authentication (name = JWT subject)
7. Invalid/missing token → 401 {"error":"unauthorized"} (SecurityConfig exception handler)
```

There is exactly one user (the owner) — no signup, no per-user accounts, no roles beyond "owner".

## Error Response Format
Always JSON:
```json
{"error": "human readable message"}
```
401 → `{"error":"unauthorized"}`, 403 → `{"error":"forbidden"}` (from `SecurityConfig`'s exception handlers); domain errors are raised as `ResponseStatusException` in services/controllers.

## Key Rules for Agents

1. **No SQL outside `*Repository` classes** — controllers and services call repositories, never raw JDBC/SQL directly.
2. **Controllers must be thin:** parse request → call service → return response body / `ResponseEntity`.
3. **Schema changes** go in a new `V<N>__description.sql` file under `src/main/resources/db/migration/` — never edit a shipped migration.
4. **New features** get their own package under `com.ivelox.core.<feature>`, following the `health/` package's shape (Controller, Service, Repository, Models).
5. **Before commit:** `./mvnw -q compile` and `./mvnw -q test` must both pass.
6. **Feature flags** live in `IveloxProperties` (e.g. `healthEnabled`) and are checked at the top of the controller method (see `HealthController.requireFeature()`).

## Environment Variables
```env
PORT=8080                        # Server port
FRONTEND_URL=                    # Allowed CORS origin(s)
JWT_SECRET=                      # Secret for self-issued owner JWT
JWT_TTL_SECONDS=                 # JWT lifetime
TELEGRAM_BOT_TOKEN=              # Bot token for OTP delivery
TELEGRAM_CHAT_ID=                # Owner's Telegram chat ID
OTP_TTL_SECONDS=                 # OTP code lifetime
OTP_MIN_INTERVAL_SECONDS=        # Minimum interval between OTP requests
HEALTH_ENABLED=                  # Feature flag for the health/* endpoints
GEMINI_API_KEY=                  # Gemini API key (food/nutrition resolution)
GEMINI_MODEL=                    # Gemini model id (e.g. gemini-2.5-flash)

# Production datasource (Postgres/Supabase via Fly)
SPRING_DATASOURCE_URL=
SPRING_DATASOURCE_USERNAME=
SPRING_DATASOURCE_PASSWORD=
SPRING_DATASOURCE_DRIVER=
```

## Database Schema (Flyway-managed)

### V1 — food cache & meal logs
| Table | Purpose |
|---|---|
| `food_cache` | Cached nutrition resolution results (text/image → macros) |
| `meal_logs` | User-logged meals (food, quantity, macros, timestamp) |

### V2 — body metrics, burns, goals
| Table | Purpose |
|---|---|
| `body_metrics` | Weight/body composition entries over time |
| `burn_logs` | Logged calorie burns (exercise) |
| `health_goals` | BMI/nutrition targets used to derive meal plan and today/weekly checks |

## Current State

### Auth (complete)
- [x] OTP request/verify via Telegram, self-issued JWT
- [x] `JwtAuthFilter` — stateless Bearer JWT auth
- [x] 401/403 JSON error responses wired in `SecurityConfig`

### Health (P1–P4 complete)
- [x] Food resolve (text/image) via Gemini, with image size/MIME validation
- [x] Meal logs (create/list/delete)
- [x] Body metrics (create/latest/history)
- [x] Goals (upsert/get) + generated meal plan
- [x] Burn logs (create/list/delete)
- [x] Today check + weekly check rollups

### Infrastructure
- [x] Flyway migrations V1, V2 against Supabase Postgres in prod
- [x] Deployed on Fly.io (`fly.toml`, Dockerfile)
- [x] Feature flag (`HEALTH_ENABLED`) gates all `/api/v1/health/**` routes

### Pending / known gaps
- [ ] `SecurityConfig.corsConfigurationSource()` uses `props.frontendUrl()` (single origin) while `IveloxProperties.allowedFrontendOrigins()` computes a list (with www/apex twins) — verify these are reconciled so CORS actually honors all intended origins.
- [ ] Legacy Go implementation removed from `legacy/go/` — this doc previously described a different (IELTS exam) domain; if any of that scope is still wanted, it needs to be re-planned against the current Spring Boot codebase.
