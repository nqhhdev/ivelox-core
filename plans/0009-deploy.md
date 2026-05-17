# Phase 8 — Deploy (Swagger + Docker + Fly.io) ❌

## Goal
Production-ready deployment on Fly.io (Singapore region) with Swagger docs and GitHub Actions CI/CD.

## Prerequisite
All feature phases complete. `go build ./...` and `go test ./...` pass clean.

## 8.1 Swagger (swaggo)

### Install
```bash
go install github.com/swaggo/swag/cmd/swag@latest
go get github.com/swaggo/gin-swagger
go get github.com/swaggo/files
```

### Add annotations to handlers
```go
// @Summary Verify JWT
// @Tags auth
// @Security BearerAuth
// @Success 200 {object} domain.User
// @Router /api/v1/auth/verify [post]
```

### Generate
```bash
swag init -g cmd/server/main.go -o docs/swagger
```

### Serve at `/swagger/*`
```go
router.GET("/swagger/*any", ginSwagger.WrapHandler(swaggerFiles.Handler))
```

## 8.2 Dockerfile
```dockerfile
FROM golang:1.23-alpine AS builder
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o server ./cmd/server

FROM alpine:3.19
WORKDIR /app
COPY --from=builder /app/server .
EXPOSE 8080
CMD ["./server"]
```

## 8.3 Fly.io Config (`fly.toml`)
```toml
app = "ivelox-core"
primary_region = "sin"  # Singapore

[build]

[http_service]
  internal_port = 8080
  force_https = true
  auto_stop_machines = true
  auto_start_machines = true
  min_machines_running = 0

[[vm]]
  memory = "256mb"
  cpu_kind = "shared"
  cpus = 1
```

### Fly.io secrets (set once)
```bash
fly secrets set PORT=8080
fly secrets set FRONTEND_URL=https://ivelox-app.vercel.app
fly secrets set SUPABASE_URL=...
fly secrets set SUPABASE_JWT_SECRET=...
fly secrets set DATABASE_URL=...
fly secrets set GEMINI_API_KEY=...
fly secrets set GROQ_API_KEY=...
fly secrets set DEEPL_API_KEY=...
```

## 8.4 GitHub Actions CI/CD (`.github/workflows/deploy.yml`)
```yaml
name: Deploy
on:
  push:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-go@v5
        with: { go-version: '1.23' }
      - run: go build ./...
      - run: go test ./...

  deploy:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: superfly/flyctl-actions/setup-flyctl@master
      - run: flyctl deploy --remote-only
        env:
          FLY_API_TOKEN: ${{ secrets.FLY_API_TOKEN }}
```

## Cost (monthly, Fly.io Singapore)
| Resource | Cost |
|---|---|
| Fly.io shared VM (256MB, auto-sleep) | ~$0 when idle / ~$3.41/mo active |
| Supabase free tier (500MB DB, 1GB storage) | $0 |
| Vercel (FE hosting) | $0 |
| Gemini 2.0 Flash | ~$0–2 |
| Groq Whisper | $0 (free tier) |
| DeepL | $0 (free tier) |
| **Total** | **~$0–5/month** |

## Tasks
- [ ] Add swaggo annotations to all handlers
- [ ] Run `swag init`, add `docs/swagger/` to `.gitignore`
- [ ] Write `Dockerfile`
- [ ] Write `fly.toml`
- [ ] Write `.github/workflows/deploy.yml`
- [ ] Create Fly.io app: `flyctl apps create ivelox-core`
- [ ] Set Fly.io secrets
- [ ] First manual deploy: `flyctl deploy`
- [ ] Verify `/api/v1/health` and `/swagger/index.html` are reachable
- [ ] Add `FLY_API_TOKEN` to GitHub Actions secrets
- [ ] Verify auto-deploy on push to main
