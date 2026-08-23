# Spring Boot Private Platform — Implementation Plan (P1 focus)

> **For agentic workers:** Use superpowers:executing-plans or implement sequentially. Checkboxes track progress.

**Goal:** Replace Go runtime in `ivelox-core` with Spring Boot owner-only OTP auth (Telegram) + JWT, ready for Health P2.

**Architecture:** Maven Spring Boot 3.3 / Java 17 at repo root; Go archived under `legacy/go/`. Stateless JWT after OTP verify; Telegram bot sends OTP to fixed chat id.

**Tech Stack:** Java 17 · Spring Boot 3.3 · Spring Security · Spring Web · Flyway (P2) · JJWT or `spring-security-oauth2-jose` · WebClient · JUnit 5

**Spec:** `docs/superpowers/specs/2026-08-23-spring-boot-private-platform-design.md`

## Global Constraints

- Owner-only OTP → `TELEGRAM_CHAT_ID` only
- No Supabase Auth
- API prefix `/api/v1`
- CORS allow `FRONTEND_URL` only
- Author commits: `nqhhdev <nqhh.dev@gmail.com>`; no `Co-Authored-By`
- Do not commit `.env`

---

## File map (P1)

| Path | Role |
|------|------|
| `legacy/go/**` | Archived Go sources |
| `pom.xml`, `mvnw*` | Maven build |
| `src/main/java/com/ivelox/core/IveloxCoreApplication.java` | Entry |
| `.../config/SecurityConfig.java` | JWT filter, public auth routes |
| `.../config/CorsConfig.java` | CORS |
| `.../auth/OtpController.java` | request/verify |
| `.../auth/OtpService.java` | generate/hash/verify/rate-limit |
| `.../auth/JwtService.java` | issue/parse |
| `.../telegram/TelegramClient.java` | sendMessage |
| `src/main/resources/application.yml` | config |
| `Dockerfile` | Spring image |
| `.env.example` | new vars |

---

### Task 1: Archive Go + Spring skeleton

- [ ] Move `cmd/`, `internal/`, `config/`, `go.mod`, `go.sum`, `*.go`-related root build files into `legacy/go/` (keep `docs/`, `.github`, `migrations/` temporarily)
- [ ] Generate Spring Boot 3.3 Maven project (web, security, validation) with package `com.ivelox.core`
- [ ] Add `GET /api/v1/health` → `{ "status": "ok" }`
- [ ] `./mvnw -q test` passes
- [ ] Commit: `chore: archive Go and scaffold Spring Boot`

### Task 2: OTP service + Telegram

- [ ] `OtpService`: 6-digit, SHA-256 hash, TTL 5m, single-use, in-memory store OK for P1 single machine; rate limit 1 req / 30s
- [ ] `TelegramClient`: `sendMessage` to `TELEGRAM_CHAT_ID`
- [ ] `POST /api/v1/auth/otp/request` → 204
- [ ] Unit test: verify accepts good code, rejects expired/wrong
- [ ] Commit: `feat(auth): OTP generation and Telegram delivery`

### Task 3: JWT + Security

- [ ] `JwtService` HS256 with `JWT_SECRET`
- [ ] `POST /api/v1/auth/otp/verify` → `{ access_token, expires_in }`
- [ ] Security filter: permit `/api/v1/health`, `/api/v1/auth/otp/**`; else JWT
- [ ] `GET /api/v1/auth/me` with JWT
- [ ] Commit: `feat(auth): JWT issue and Spring Security filter`

### Task 4: Dockerfile + env example

- [ ] Multi-stage Dockerfile (eclipse-temurin 17, `mvn -DskipTests package`, run jar)
- [ ] Update `.env.example` and README stub
- [ ] Commit: `chore: Spring Boot Docker and env example`

### Later (separate plan / continue)

- **P2:** Flyway + Health APIs + Gemini  
- **P3:** FE portfolio + OTP login  
- **P4:** Fly cutover  

---

## Spec coverage (P1)

| Spec item | Task |
|-----------|------|
| Spring in ivelox-core | 1 |
| OTP + Telegram owner-only | 2 |
| JWT security | 3 |
| Docker | 4 |
| Health APIs | P2 |
| FE portfolio | P3 |
