# iVelox Core — Spring Boot Migration + Private Platform Design

**Date:** 2026-08-23  
**Status:** Approved  
**Author:** nqhhdev  

## 1. Overview

Migrate `ivelox-core` from Go to **Java 17 + Spring Boot 3.3**. Product shape:

- Public **portfolio** at FE `/` (GitHub hybrid + manual overrides)
- Private **OTP login** via Telegram (owner-only)
- **Health** feature only (IELTS removed), gated by JWT
- Feature flags: enable/disable Health without redeploying auth

## 2. Goals / Non-goals

**Goals**
- Replace Go HTTP API with Spring Boot in the same repo (`ivelox-core`)
- Owner-only auth: OTP → Telegram → JWT
- Re-implement Health P1 APIs (food resolve hybrid cache+AI, meals, today)
- FE: portfolio home, OTP login, JWT-gated `/health`

**Non-goals**
- Multi-user Telegram OTP
- Supabase Auth (project ref currently dead / NXDOMAIN)
- IELTS / jobfinder / scraper in the new runtime (Go code archived under `legacy/`)
- AWS migration

## 3. Architecture

```
Browser
  /              → static portfolio (FE)
  /login         → OTP UI → Spring /api/v1/auth/otp/*
  /health/*      → Bearer JWT → Spring /api/v1/health/*

Spring Boot (ivelox-core)
  AuthModule     → OtpService, TelegramNotifier, JwtService
  HealthModule   → FoodResolve, MealLog, GeminiNutrition
  Postgres       → Flyway migrations
```

**Security**
- OTP: 6 digits, SHA-256 hash stored, TTL 5–10 min, single use, rate limit request
- JWT: HS256, short access TTL (e.g. 1h), secret `JWT_SECRET`
- All `/api/v1/health/**` require valid JWT
- CORS: `FRONTEND_URL` only
- Owner-only: OTP always sent to fixed `TELEGRAM_CHAT_ID` (no user picker)

## 4. API (Spring)

### Auth
| Method | Path | Auth | Body / notes |
|--------|------|------|----------------|
| POST | `/api/v1/auth/otp/request` | public | empty; rate-limited; sends Telegram |
| POST | `/api/v1/auth/otp/verify` | public | `{ "code": "123456" }` → `{ access_token, expires_in }` |
| GET | `/api/v1/auth/me` | JWT | `{ "role": "owner" }` |

### Health (JWT required) — keep P1 shapes
| Method | Path |
|--------|------|
| POST | `/api/v1/health/foods/resolve` |
| POST | `/api/v1/health/meals` |
| GET | `/api/v1/health/meals?date=` |
| DELETE | `/api/v1/health/meals/:id` |
| GET | `/api/v1/health/check/today?date=` |

Civil day: `Asia/Ho_Chi_Minh`. Image base64 max 3MB; MIME allowlist.

### Platform
| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/v1/health` | liveness `{ status: ok }` (unauthenticated) |
| GET | `/api/v1/features` | `{ health: { enabled: true, auth_required: true } }` |

## 5. Data

Flyway under `src/main/resources/db/migration/`:
- `V1__food_cache_meal_logs.sql` (from prior Go migrations + RLS revoke)
- Optional `otp_challenges` table if not in-memory (prefer DB for multi-instance Fly)

Auth no longer uses Supabase Auth tables. Profiles optional later.

## 6. Config / secrets

```
PORT=8080
FRONTEND_URL=
DATABASE_URL=   # or SPRING_DATASOURCE_URL
JWT_SECRET=
TELEGRAM_BOT_TOKEN=
TELEGRAM_CHAT_ID=
GEMINI_API_KEY=
HEALTH_ENABLED=true
```

## 7. Frontend (`ivelox-app`)

- `/` portfolio: fetch GitHub `nqhhdev` + `src/features/portfolio/content.ts` overrides
- `/login` OTP only (remove Google/email)
- Remove IELTS/onboarding routes from primary nav; redirect legacy paths
- `apiClient` attaches JWT from auth store; 401 → `/login`
- Feature flag from `/api/v1/features` before showing Health

## 8. Repo migration strategy

1. Branch `feat/spring-boot-migrate`
2. Move existing Go tree → `legacy/go/` (reference only, not built)
3. Add Spring Boot Maven project at repo root (`pom.xml`, `mvnw`, `src/`)
4. Replace Dockerfile for Spring
5. Update README/CLAUDE.md
6. Cutover Fly deploy when green

## 9. Phases

| Phase | Deliverable |
|-------|-------------|
| P1 | Spring skeleton, OTP+Telegram+JWT, `/auth/*`, security filter |
| P2 | Flyway + Health APIs + Gemini |
| P3 | FE portfolio + OTP login + Health gate |
| P4 | Docker/Fly cutover |

## 10. Open ops

- Create/restore Postgres (new Supabase project or other) — current ref NXDOMAIN
- Set Fly secrets for Spring app
- Apply Flyway on boot or manually once
