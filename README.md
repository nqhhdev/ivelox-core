# iVelox — Backend API

Go + Gin backend for iVelox, an IELTS learning and practice platform.

## Tech Stack
- Go 1.22 + Gin
- Clean Architecture (domain → usecase → repository → delivery)
- Supabase (PostgreSQL + Auth JWT verification)
- OpenAI Whisper (speaking transcription)
- Claude API (AI scoring)
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
  infrastructure/  -- third-party clients (Supabase, OpenAI, Claude, DeepL)
```

## Getting Started
```bash
cp .env.example .env  # fill in your keys
go mod tidy
go run ./cmd/server/
```
