# iVelox — Personal Backend Platform

Go + Gin personal backend platform. Multi-service architecture — auth foundation with pluggable services (job finder, bots, tools, etc.).

**Production:** `https://api.i-velox.app`

## Tech Stack

- Go 1.22+ + Gin
- Clean Architecture (domain → usecase → repository → delivery)
- Supabase (PostgreSQL + Auth JWT)
- Telegram Bot API — notifications & commands
- Fly.io (Singapore region)

## Project Structure

```
cmd/server/          — HTTP API entry point
config/              — env var loading
internal/
  domain/            — pure Go structs + repository interfaces
  usecase/           — business logic
  repository/        — PostgreSQL implementations
  delivery/http/     — Gin handlers + router
  middleware/        — auth (JWT), CORS
  infrastructure/    — Supabase auth client
  telegram/          — Telegram bot shell
tests/
  integration/       — integration tests (real DB)
```

## Getting Started

```bash
cp .env.example .env   # fill in your keys
go mod tidy
go run ./cmd/server/
```

Server starts at `http://localhost:8080`

```bash
curl http://localhost:8080/api/v1/health
# {"status":"ok"}
```

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `PORT` | no | HTTP port (default: 8080) |
| `FRONTEND_URL` | no | CORS allowed origin |
| `SUPABASE_URL` | yes | Supabase project URL |
| `SUPABASE_ANON_KEY` | yes | Supabase anon key |
| `SUPABASE_JWT_SECRET` | yes | JWT secret for token verification |
| `DATABASE_URL` | yes | PostgreSQL connection string |
| `TELEGRAM_TOKEN` | no | Telegram bot token |
| `TELEGRAM_CHAT_ID` | no | Telegram chat ID for notifications |

## API

Base path: `/api/v1`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/health` | — | Health check |
| POST | `/auth/register` | — | Create account |
| POST | `/auth/login` | — | Email/password login |
| POST | `/auth/refresh` | — | Refresh access token |
| POST | `/auth/verify` | Bearer | Verify JWT + sync profile |
| POST | `/auth/logout` | Bearer | Revoke session |

## Testing

```bash
# Unit tests
export PATH="/opt/homebrew/bin:$PATH" && go test ./... -v

# With race detector
go test ./... -race -count=1

# Integration tests (requires .env)
export $(cat .env | grep -v '^#' | xargs) && go test -tags integration ./tests/... -v
```

## Deploy

```bash
flyctl secrets set SUPABASE_URL=... SUPABASE_JWT_SECRET=... DATABASE_URL=...
flyctl deploy
```

See `docs/deployment.md` for full guide.

## CI/CD

| Workflow | Trigger | Jobs |
|---|---|---|
| `ci.yml` | Push & PR | build + test |
| `deploy.yml` | Push to `main` | build → test → deploy |
| `review.yml` | PRs | gofmt + vet + staticcheck |
