# Spring Boot cutover (P4)

**Date:** 2026-08-23  
**Branch:** `feat/spring-boot-migrate`

## Pre-flight

1. Provision Postgres (new Supabase project or other). Old ref `zvcpgyzwmwwmredwzgcy` is dead (NXDOMAIN).
2. Convert connection to JDBC:
   ```
   SPRING_DATASOURCE_URL=jdbc:postgresql://HOST:5432/postgres
   SPRING_DATASOURCE_USERNAME=...
   SPRING_DATASOURCE_PASSWORD=...
   SPRING_DATASOURCE_DRIVER=org.postgresql.Driver
   ```
3. Set Fly secrets (do **not** commit):
   ```bash
   fly secrets set \
     JWT_SECRET=... \
     TELEGRAM_BOT_TOKEN=... \
     TELEGRAM_CHAT_ID=... \
     FRONTEND_URL=https://your-fe.domain \
     GEMINI_API_KEY=... \
     HEALTH_ENABLED=true \
     SPRING_DATASOURCE_URL=... \
     SPRING_DATASOURCE_USERNAME=... \
     SPRING_DATASOURCE_PASSWORD=... \
     SPRING_DATASOURCE_DRIVER=org.postgresql.Driver
   ```
4. FE: set `VITE_API_URL` to the Fly app URL; remove Supabase env vars.

## Deploy

```bash
# from ivelox-core on feat/spring-boot-migrate (or after merge to main)
./mvnw -B test
fly deploy --remote-only
```

Flyway runs `V1__food_cache_meal_logs.sql` on boot.

## Smoke

```bash
curl -s "$API/api/v1/health"          # {"status":"ok"}
curl -s "$API/api/v1/features"        # health.enabled
curl -s -X POST "$API/api/v1/auth/otp/request"  # Telegram OTP
# verify → JWT → GET /api/v1/auth/me
# JWT → Health meals CRUD
```

## Rollback

Go runtime archived at `legacy/go/`. Rollback = redeploy previous image / restore Go Dockerfile from git history before Spring commit. Jobfinder remains legacy-only (not in Spring runtime).

## CI

- `ci.yml` / `deploy.yml` use JDK 21 + `./mvnw test` (no Go).
