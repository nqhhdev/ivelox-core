# Deployment Guide

## Architecture Overview

Two independent services, both deployed on Fly.io (Singapore region):

| Service | App | Config | Purpose |
|---|---|---|---|
| API Server | `ivelox-core` | `fly.toml` | REST API + Supabase Auth |
| Job Finder | `ivelox-jobfinder` | `fly.jobfinder.toml` | Telegram bot + job scraper |

---

## Database Schema (Supabase)

### `auth` schema — managed by Supabase
Users are authenticated via Supabase Auth (JWT). No manual management needed.

### `public.profiles` — user profile (linked to `auth.users`)
| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` | FK → `auth.users.id` |
| `display_name` | `text` | nullable |
| `email` | `text` | nullable |
| `avatar_url` | `text` | nullable |
| `role` | `user_role` | default `'user'` |
| `provider` | `text` | default `'email'` |
| `created_at` | `timestamptz` | auto |
| `updated_at` | `timestamptz` | auto |

### `job_finder.seen_jobs` — deduplication store
| Column | Type | Notes |
|---|---|---|
| `url_hash` | `text` | PK — MD5 of apply URL |
| `title` | `text` | |
| `company` | `text` | |
| `source` | `text` | remotive/arbeitnow/themuse/topdev/itviec |
| `score` | `int` | Gemini match score 0–100 |
| `notified_at` | `timestamptz` | auto |

### `job_finder.profile` — candidate requirements (single row, id=1)
| Column | Type | Notes |
|---|---|---|
| `id` | `int` | always 1 (single row) |
| `name` | `text` | candidate name |
| `role` | `text` | target role |
| `skills` | `text` | comma-separated skills |
| `location` | `text` | preferred locations |
| `salary_min` | `int` | minimum monthly salary (USD) |
| `languages` | `text` | spoken languages |
| `extra` | `text` | additional requirements |
| `updated_at` | `timestamptz` | auto |

Profile is editable via Telegram commands — see [Telegram Bot](#telegram-bot-commands) below.

---

## Environment Variables

```env
PORT=8080
FRONTEND_URL=https://your-frontend.com

# Supabase
SUPABASE_URL=https://<project>.supabase.co
SUPABASE_ANON_KEY=<anon-key>
SUPABASE_JWT_SECRET=<jwt-secret>
DATABASE_URL=postgresql://postgres.<project>:<password>@aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres

# Telegram
TELEGRAM_TOKEN=<bot-token>
TELEGRAM_CHAT_ID=<chat-id>

# AI
GEMINI_API_KEY=<gemini-key>
```

---

## Run Locally

```bash
git clone https://github.com/nqhhdev/ivelox-core
cd ivelox-core
cp .env.example .env   # fill in your keys
go mod tidy

# API server
go run ./cmd/server/

# Job finder bot (separate terminal)
go run ./cmd/jobfinder/
```

```bash
curl http://localhost:8080/api/v1/health
# {"status":"ok"}
```

---

## Docker

Two build targets in the Dockerfile:

```bash
# API server
docker build --target server -t ivelox-server .
docker run -p 8080:8080 --env-file .env ivelox-server

# Job finder
docker build --target jobfinder -t ivelox-jobfinder .
docker run --env-file .env ivelox-jobfinder
```

---

## Fly.io Deploy

### API Server (`fly.toml`)

```bash
flyctl secrets set \
  SUPABASE_URL=... \
  SUPABASE_ANON_KEY=... \
  SUPABASE_JWT_SECRET=... \
  DATABASE_URL=... \
  --app ivelox-core

fly deploy --config fly.toml
flyctl logs --app ivelox-core
```

### Job Finder (`fly.jobfinder.toml`)

```bash
flyctl apps create ivelox-jobfinder --org personal

flyctl secrets set \
  DATABASE_URL=... \
  GEMINI_API_KEY=... \
  TELEGRAM_TOKEN=... \
  TELEGRAM_CHAT_ID=... \
  SUPABASE_URL=... \
  SUPABASE_ANON_KEY=... \
  SUPABASE_JWT_SECRET=... \
  --app ivelox-jobfinder

fly deploy --config fly.jobfinder.toml
flyctl logs --app ivelox-jobfinder
```

---

## Telegram Bot Commands

Send these commands to your bot to manage job search requirements:

| Command | Example | Effect |
|---|---|---|
| `/profile` | `/profile` | View current candidate profile |
| `/setrole` | `/setrole Senior Flutter Developer` | Update target role |
| `/setskills` | `/setskills Flutter, Kotlin, Swift` | Update skills |
| `/setlocation` | `/setlocation Remote, Vietnam` | Update preferred location |
| `/setsalary` | `/setsalary 3000` | Set minimum salary (USD/mo) |
| `/setlang` | `/setlang Vietnamese, English` | Update languages |
| `/setextra` | `/setextra Open to part-time` | Extra requirements |
| `/done` | `/done` | End current AI chat session |
| `/help` | `/help` | Show all commands |

Each `/set*` command shows the **before** and **after** values and applies changes immediately to the next scoring cycle.

To chat about a specific job, tap the **💬 Chat with AI** button on any job notification. Send `/done` to end the session.

---

## CI/CD (GitHub Actions)

| Workflow | Trigger | Jobs |
|---|---|---|
| `ci.yml` | Every push & PR | `go build` + `go test -race` |
| `deploy.yml` | Push to `main` | build → test → `flyctl deploy` |

### Required GitHub Secrets

Go to: `github.com/nqhhdev/ivelox-core` → Settings → Secrets → Actions

| Secret | How to get |
|---|---|
| `FLY_API_TOKEN` | `flyctl tokens create deploy` |
| `SUPABASE_JWT_SECRET` | Supabase dashboard → Project Settings → JWT |
| `DATABASE_URL` | Supabase dashboard → Project Settings → Database |
| `GEMINI_API_KEY` | Google AI Studio → API Keys |
| `TELEGRAM_TOKEN` | @BotFather on Telegram |
| `TELEGRAM_CHAT_ID` | Your Telegram chat ID |
