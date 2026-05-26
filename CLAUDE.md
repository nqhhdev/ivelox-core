# iVelox Backend — Claude Instructions

## Project
Personal backend platform. Go + Gin + Clean Architecture.
Multi-service: auth foundation + pluggable services (Telegram bot, job finder, tools, etc.)

## Git rules
- Author: nqhhdev <nqhh.dev@gmail.com> — always, no exceptions
- Never add `Co-Authored-By` in commit messages
- Never commit `.env`
- Never push directly to `main` — always create a feature branch and open a PR
- Branch naming: `feature/<short-description>`, `fix/<short-description>`, `chore/<short-description>`
- PR required for all changes to `main`, no exceptions

## Architecture — Clean Architecture (strict)
```
domain/       → pure Go structs + interfaces, zero external imports
usecase/      → business logic, imports domain only
repository/   → implements domain interfaces, talks to PostgreSQL
delivery/http → Gin handlers: parse request → call usecase → render JSON
infrastructure → third-party clients (Supabase JWT)
telegram/     → Telegram bot shell, extensible for new commands
```
- Dependencies flow inward only: delivery → usecase → domain ← repository
- All external dependencies injected via interfaces in domain/
- Never import gin/pgx/etc in domain or usecase packages

## Stack
- Go 1.22+ + Gin
- pgx/v5 — PostgreSQL driver (Supabase)
- golang-jwt/v5 — JWT verification (Supabase tokens)
- godotenv — env loading
- go-telegram-bot-api/v5 — Telegram bot

## Environment variables (required)
```
PORT=8080
FRONTEND_URL=
SUPABASE_URL=
SUPABASE_ANON_KEY=
SUPABASE_JWT_SECRET=
DATABASE_URL=
TELEGRAM_TOKEN=
TELEGRAM_CHAT_ID=
```

## Code rules
- Go binary: `/opt/homebrew/bin/go` on macOS (always export PATH)
- Run `go build ./...` before committing — zero tolerance for compile errors
- Run `go test ./...` before committing
- Handler functions must be thin: parse → call usecase → respond
- No SQL in handlers or usecases — SQL only in repository/postgres/
- Error messages in JSON: `{"error": "message"}` format
- All protected routes use `middleware.Auth(jwtSecret)`

## Adding new services
New features (job finder, schedulers, bots) go in their own package under `internal/`.
Each service gets its own domain interfaces if it needs DB access.
Wire everything up in `cmd/server/main.go`.

## Testing
- Use fake/in-memory repos for unit tests (no real DB)
- Test files: `*_test.go` in same package with `_test` suffix
- Run: `export PATH="/opt/homebrew/bin:$PATH" && go test ./... -v`

## API conventions
- Base path: `/api/v1`
- Auth header: `Authorization: Bearer <supabase-jwt>`
- Protected routes grouped under `middleware.Auth`
- Health check: `GET /api/v1/health` — always returns `{"status":"ok"}`

## Docs
- Deployment guide: `docs/deployment.md`
- CI/CD guide: `docs/github-cicd.md`
