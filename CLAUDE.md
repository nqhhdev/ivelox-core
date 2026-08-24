# iVelox Backend — Claude Instructions

## Project
Personal backend platform. Java 21 + Spring Boot 4 + layered architecture.
Single-owner platform: Telegram-OTP auth, health tracking (food/meals/body/burns/goals), platform/feature flags.

## Git rules
- Author: nqhhdev <nqhh.dev@gmail.com> — always, no exceptions
- Never add `Co-Authored-By` in commit messages
- Never commit `.env`
- Never push directly to `main` — always create a feature branch and open a PR
- Branch naming: `feature/<short-description>`, `fix/<short-description>`, `chore/<short-description>`
- PR required for all changes to `main`, no exceptions

## Architecture
```
com.ivelox.core.auth      → OTP login via Telegram, JWT issuing/verification
com.ivelox.core.config    → Spring configuration (security, properties, app config)
com.ivelox.core.health    → food/meals/body-metrics/burns/goals domain + controller + repositories
com.ivelox.core.platform  → platform/feature-flag endpoints
com.ivelox.core.telegram  → Telegram bot client (OTP delivery)
```
- Controllers are thin: parse request → call service → render JSON
- No SQL in controllers — SQL lives in `*Repository` classes (JdbcClient/JdbcTemplate)
- Schema managed by Flyway migrations in `src/main/resources/db/migration/`

## Stack
- Java 21 + Spring Boot 4 (starters: web, security, validation, jdbc, actuator, flyway)
- Flyway + PostgreSQL (Supabase) in prod, H2 file DB for local dev
- jjwt — JWT issuing/verification (self-issued, not Supabase-verified)
- go-telegram-bot-api-equivalent via `TelegramClient` — OTP delivery over Telegram
- Gemini API — nutrition resolution for food logging

## Environment variables (required)
```
PORT=8080
FRONTEND_URL=
JWT_SECRET=
JWT_TTL_SECONDS=
TELEGRAM_BOT_TOKEN=
TELEGRAM_CHAT_ID=
OTP_TTL_SECONDS=
OTP_MIN_INTERVAL_SECONDS=
HEALTH_ENABLED=
GEMINI_API_KEY=
GEMINI_MODEL=
# Prod Postgres (Fly/Supabase):
SPRING_DATASOURCE_URL=
SPRING_DATASOURCE_USERNAME=
SPRING_DATASOURCE_PASSWORD=
SPRING_DATASOURCE_DRIVER=
```

## Code rules
- Run `./mvnw -q compile` before committing — zero tolerance for compile errors
- Run `./mvnw -q test` before committing
- Controller methods must be thin: parse → call service → respond
- No SQL in controllers or services — SQL only in `*Repository` classes
- Error messages in JSON: `{"error": "message"}` format
- All protected routes go through `JwtAuthFilter` / Spring Security config in `SecurityConfig`

## Adding new services
New features go in their own package under `com.ivelox.core.<feature>`.
Each service gets its own repository if it needs DB access, plus a Flyway migration for schema changes.
Wire security/config changes in `SecurityConfig` / `IveloxProperties`.

## Testing
- Use fakes/mocks for unit tests where possible; `spring-boot-starter-*-test` starters available for slice tests
- Test files: `*Test.java` under `src/test/java`, mirroring the main package structure
- Run: `./mvnw test`

## API conventions
- Base path: `/api/v1`
- Auth header: `Authorization: Bearer <jwt>`
- Protected routes enforced via `JwtAuthFilter`
- Health check: `GET /actuator/health` — returns Spring Boot actuator health status

## Docs
- Deployment guide: `docs/deployment.md`
- CI/CD guide: `docs/github-cicd.md`
