# Deployment Guide

## Run Locally

```bash
git clone https://github.com/nqhhdev/ivelox-core
cd ivelox-core
cp .env.example .env   # fill in your keys
go mod tidy
go run ./cmd/server/
```

```bash
curl http://localhost:8080/api/v1/health
# {"status":"ok"}
```

---

## Docker

```bash
docker build -t ivelox-core .

docker run -p 8080:8080 \
  -e PORT=8080 \
  -e SUPABASE_URL=... \
  -e SUPABASE_ANON_KEY=... \
  -e SUPABASE_JWT_SECRET=... \
  -e DATABASE_URL=... \
  ivelox-core
```

---

## Fly.io (current)

### First deploy

```bash
brew install flyctl
fly auth login
cd ivelox-core
fly launch   # follow prompts, pick Singapore region (sin)
```

### Set secrets

```bash
flyctl secrets set \
  SUPABASE_URL=... \
  SUPABASE_ANON_KEY=... \
  SUPABASE_JWT_SECRET=... \
  DATABASE_URL=... \
  TELEGRAM_TOKEN=... \
  TELEGRAM_CHAT_ID=...
```

### Deploy

```bash
flyctl deploy
```

### Logs

```bash
flyctl logs --app ivelox-core
```

---

## CI/CD (GitHub Actions)

Four workflows in `.github/workflows/`:

| Workflow | Trigger | Jobs |
|---|---|---|
| `ci.yml` | Every push & PR | `go build` + `go test -race` |
| `deploy.yml` | Push to `main` | build → test → `flyctl deploy` |
| `review.yml` | PRs only | `gofmt` + `go vet` + `staticcheck` |
| `integration.yml` | Push to `main` & PRs | Supabase branch DB integration tests |

### Required GitHub Secrets

Go to: `github.com/nqhhdev/ivelox-core` → Settings → Secrets → Actions

| Secret | How to get |
|---|---|
| `FLY_API_TOKEN` | `flyctl tokens create deploy` |
| `SUPABASE_ACCESS_TOKEN` | supabase.com → Account → Access Tokens |
| `SUPABASE_JWT_SECRET` | Supabase dashboard → Project Settings → JWT |
