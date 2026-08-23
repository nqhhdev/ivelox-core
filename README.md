# iVelox Core — Spring Boot

Java 21 + Spring Boot 4 private API (OTP Telegram auth + Health).

## Run locally

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21   # or your JDK 21
cp .env.example .env   # fill secrets
./mvnw spring-boot:run
```

Health: `GET http://localhost:8080/api/v1/health`

## Auth (owner-only)

1. `POST /api/v1/auth/otp/request` — sends OTP to `TELEGRAM_CHAT_ID`
2. `POST /api/v1/auth/otp/verify` `{"code":"123456"}` — returns JWT
3. Use `Authorization: Bearer <token>` on protected routes

## Legacy Go

Previous Go implementation is under `legacy/go/` (not built).

## Docs

- Spec: `docs/superpowers/specs/2026-08-23-spring-boot-private-platform-design.md`
- Plan P1: `docs/superpowers/plans/2026-08-23-spring-boot-p1-otp-auth.md`
- Plan P2: `docs/superpowers/plans/2026-08-23-spring-boot-p2-health-apis.md`
- Plan P4 cutover: `docs/superpowers/plans/2026-08-23-spring-boot-p4-fly-cutover.md`
