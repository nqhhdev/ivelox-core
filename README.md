# iVelox — Backend API

Go + Gin backend for iVelox, an IELTS learning and practice platform.

**Production:** `https://api.i-velox.app`

## Tech Stack
- Go 1.23 + Gin
- Clean Architecture (domain → usecase → repository → delivery)
- Supabase (PostgreSQL + Auth JWT verification)
- Fly.io (Singapore region)
- OpenAI Whisper (speaking transcription)
- Gemini 2.0 Flash (AI scoring)
- DeepL (translation)

## Project Structure
```
cmd/server/        -- entry point
config/            -- env var loading
internal/
  domain/          -- pure Go structs + repository interfaces
  usecase/         -- business logic
  repository/      -- PostgreSQL implementations
  delivery/http/   -- Gin handlers + router
  middleware/      -- auth (JWT), CORS
  infrastructure/  -- third-party clients (Supabase, Gemini, DeepL)
tests/
  integration/     -- integration tests (real DB, build tag: integration)
```

## Getting Started
```bash
cp .env.example .env  # fill in your keys
go mod tidy
go run ./cmd/server/
```

## Testing

```bash
# Unit tests
go test ./... -v -race -count=1

# Integration tests (requires DATABASE_URL + SUPABASE_JWT_SECRET in .env)
export $(cat .env | grep -v '^#' | xargs) && go test -tags integration ./tests/... -v -count=1

# With coverage
go test ./... -cover -count=1
```

## CI/CD

| Workflow | Trigger | Jobs |
|---|---|---|
| `ci.yml` | Every push & PR | build + test |
| `deploy.yml` | Push to `main` | build → test → deploy to Fly.io |
| `review.yml` | PRs | gofmt + go vet + staticcheck |
| `integration.yml` | Push to `main` & PRs | Supabase branch DB integration tests |

## Docs
- Deployment guide: `docs/deployment.md`
- CI/CD guide: `docs/github-cicd.md`
- Architecture: `claude/architecture.md`
