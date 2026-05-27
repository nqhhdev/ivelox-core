# Job Finder Bot — Design Spec

**Date:** 2026-05-25
**Status:** Approved
**Author:** nqhhdev

---

## Overview

A cron job that runs every 15 minutes, fetches job listings from multiple platforms (free APIs + VN scrapers), deduplicates, scores each JD against the owner's CV profile using Gemini AI, and sends matched jobs (score ≥ 60) to a Telegram chat as individual detailed messages.

No authentication required. Single-user system (owner only).

---

## Goals

- Find Flutter/mobile/senior software engineer jobs across all major platforms
- Prioritise remote/hybrid roles
- AI scoring so only genuinely relevant jobs get notified
- Each notification includes full details + direct apply link
- No duplicate notifications across cron runs
- Interactive AI chat per job for deeper evaluation

---

## Profile (hardcoded in scorer prompt)

Used by Gemini to score each JD:

- **Role:** Mobile Software Engineer / Flutter Developer
- **Experience:** 6+ years Flutter, Swift (6 months), Dart
- **Architecture:** Clean Architecture, MVVM, MVC, MVP
- **Frameworks:** Bloc, Riverpod, GetIt, Hive, Dio, GoRouter, Firebase, Background tasks, Isolates
- **iOS native:** CoreData, MapKit, SwiftUI, APN Notifications, NSE
- **CI/CD:** GitLab CI, Fastlane, GitHub Actions
- **Agile:** Scrum Master experience
- **PO experience:** roadmap building, user data analysis
- **Release manager:** iOS, Android, Huawei AppGallery
- **Web3:** MetaMask, WalletConnect, SubWallet integration
- **Preferred:** Remote / Hybrid / Part-time (job2)

---

## Architecture

```
Cron ticker (15 min)
        │
        ▼
  Fetcher Layer  ──── parallel goroutines ────
  ┌────────────┐ ┌───────────┐ ┌───────────┐
  │ Remotive   │ │ Arbeitnow │ │ The Muse  │
  │ (free,     │ │ (free,    │ │ (free,    │
  │  no key)   │ │  no key)  │ │  no key)  │
  └────────────┘ └───────────┘ └───────────┘
  ┌────────────┐ ┌───────────┐
  │ TopDev     │ │ ITviec    │
  │ (scraper)  │ │ (scraper) │
  └────────────┘ └───────────┘
        │
        │  []RawJob
        ▼
  Deduplicator
    hash(apply_url) → seen_jobs table
    drop already-notified jobs
        │
        │  []NewJob
        ▼
  AI Scorer (Gemini 2.5 Flash Lite)
    per job: score 0–100, match_reasons[], gap_skills[]
    drop score < 60
        │
        │  []ScoredJob  sorted desc by score
        ▼
  Telegram Notifier
    1 message per job, rate-limited 1/sec
        │
        ▼
  Mark seen_jobs in Supabase
```

---

## Data Sources

### Free APIs (no key required)

| Source | URL | Notes |
|--------|-----|-------|
| Remotive | `https://remotive.com/api/remote-jobs` | Remote only, filter by category |
| Arbeitnow | `https://arbeitnow.com/api/job-board-api` | EU + remote, good for senior roles |
| The Muse | `https://www.themuse.com/api/public/jobs` | Global, filters by level/category |

### Scrapers (VN platforms)

| Source | Method | Notes |
|--------|--------|-------|
| TopDev | HTTP + HTML parse | `https://topdev.vn/jobs` — IT-focused VN jobs |
| ITviec | HTTP + HTML parse | `https://itviec.com/it-jobs` — IT-focused VN jobs |

### Search keywords used across all sources

```
flutter, mobile developer, mobile engineer, ios developer,
dart developer, react native, cross-platform, senior mobile,
mobile lead, software engineer mobile
```

---

## Deduplication

**Table:** `job_finder.seen_jobs`

```sql
create schema if not exists job_finder;

create table job_finder.seen_jobs (
    url_hash    text primary key,       -- md5(apply_url)
    title       text not null,
    company     text not null,
    source      text not null,          -- remotive | arbeitnow | themuse | topdev | itviec
    score       int  not null,
    notified_at timestamptz not null default now()
);

-- auto-cleanup: delete entries older than 30 days
-- run via pg_cron or on each insert cycle
```

**Logic:**
1. Compute `md5(apply_url)` for each fetched job
2. Batch query `seen_jobs` for all hashes
3. Drop any job whose hash already exists
4. After notifying, insert new hashes

---

## AI Scoring

**Model:** Gemini 2.5 Flash Lite (free tier — 1500 req/day, 15 req/min)

**Rate:** Each cron run fetches ~50–150 jobs. After dedup, typically 10–30 new. Well within limits.

**Prompt structure:**

```
You are a job matching assistant. Score how well this job matches the candidate profile.

CANDIDATE PROFILE:
[hardcoded profile summary]

JOB LISTING:
Title: {title}
Company: {company}
Location: {location}
Description: {description}

Respond in JSON only:
{
  "score": 0-100,
  "match_reasons": ["reason1", "reason2"],
  "gap_skills": ["skill1"],
  "work_type": "remote|hybrid|onsite|unknown",
  "seniority": "junior|mid|senior|lead|unknown"
}

Scoring guide:
- 80–100: Strong match, apply immediately
- 60–79: Good match, worth considering
- 40–59: Partial match, missing key requirements
- 0–39: Poor match, skip

Only pass score >= 60 to notification.
```

**Batch strategy:** Score jobs concurrently with max 5 goroutines (respect 15 req/min limit).

---

## Telegram Notification Format

One message per job, sent in descending score order:

```
🟢 Match: 87/100 · Remote

*Senior Flutter Developer*
🏢 Grab  ·  🌏 Remote (SEA)
💰 $3,000 – $5,000/month
📌 Source: Remotive

✅ *Why you match:*
• 6yr Flutter — senior requirement met
• Clean Architecture experience
• CI/CD + release manager background

⚠️ *Skill gaps:*
• Kotlin (nice-to-have)

[👉 Apply Now](https://...)
```

**Score badge:**
- 🟢 80–100
- 🟡 60–79

**Rate limit:** 1 message/second (Telegram Bot API limit).

**Batch summary header** (sent before job messages if ≥ 3 jobs found):
```
🔍 Found 5 new matches this run (15:30)
```

---

## Interactive Job Chat

After receiving a job notification, the owner can open a conversation with AI about that specific job.

### Flow

```
Job notification sent
        │
        ▼
[💬 Chat about this job]  ← inline button on each job message
        │  (tap)
        ▼
Bot replies: "Ask me anything about this job 👇"
        │
        ▼
Owner types any question:
  "Mình có đủ qualify không?"
  "Salary range này ở VN có ổn không?"
  "Họ dùng Kotlin, mình có cần lo không?"
  "Viết cover letter cho job này"
  "So với job trước tao nhận được thì cái nào tốt hơn?"
        │
        ▼
Gemini receives: job full context + CV profile + conversation history + question
        │
        ▼
Bot replies in same chat thread
        │
        ▼
Continue multi-turn until owner sends /done or taps another job's [💬 Chat]
```

### Session State (in-memory)

Chat sessions are stored in-memory in the bot (map keyed by chatID). Each session holds:

```go
type ChatSession struct {
    JobID     string          // url_hash of the job
    Job       ScoredJob       // full job context
    History   []ChatMessage   // conversation turns
    StartedAt time.Time
}

type ChatMessage struct {
    Role    string // "user" | "model"
    Content string
}
```

Sessions expire after 30 minutes of inactivity. No DB persistence needed — if bot restarts, session resets gracefully.

### Gemini prompt for chat

```
You are a career advisor helping a mobile software engineer evaluate a job opportunity.

CANDIDATE PROFILE:
[hardcoded profile]

JOB CONTEXT:
Title: {title}
Company: {company}
Location: {location}
Salary: {salary}
Match score: {score}/100
Match reasons: {match_reasons}
Skill gaps: {gap_skills}
Full description: {description}

CONVERSATION HISTORY:
{history}

USER QUESTION:
{question}

Answer in Vietnamese or English (match the language the user uses).
Be direct and practical. Max 300 words per reply.
```

### Notification format update

Each job message adds a `💬 Chat` button alongside the apply link:

```
🟢 Match: 87/100 · Remote

*Senior Flutter Developer*
🏢 Grab  ·  🌏 Remote (SEA)
💰 $3,000 – $5,000/month
📌 Source: Remotive

✅ *Why you match:*
• 6yr Flutter — senior requirement met
• Clean Architecture experience
• CI/CD + release manager background

⚠️ *Skill gaps:*
• Kotlin (nice-to-have)

[👉 Apply Now](https://...)  [💬 Chat with AI](callback)
```

### Commands

| Command | Action |
|---------|--------|
| `/done` | End current chat session |
| Tap `💬 Chat` on another job | Switches session to that job |

### Package additions

```
internal/
  jobfinder/
    chat/
      session.go    ← ChatSession struct, in-memory store, expiry
      gemini.go     ← multi-turn Gemini conversation handler
```

Wired into `internal/telegram/bot.go` — the bot handles `💬 Chat` callbacks and routes free-text messages to the active session's Gemini handler.

---

## Package Structure

```
internal/
  jobfinder/
    fetcher/
      types.go          ← RawJob struct, Fetcher interface
      remotive.go       ← Remotive API client
      arbeitnow.go      ← Arbeitnow API client
      themuse.go        ← The Muse API client
      topdev.go         ← TopDev HTML scraper
      itviec.go         ← ITviec HTML scraper
    scorer/
      types.go          ← ScoredJob struct
      gemini.go         ← Gemini scoring, prompt, JSON parse
    dedup/
      repository.go     ← seen_jobs CRUD (pgxpool)
    notifier/
      telegram.go       ← format message + send via bot
    chat/
      session.go        ← ChatSession struct, in-memory store, 30min expiry
      gemini.go         ← multi-turn Gemini conversation handler
    runner.go           ← orchestrates fetch→dedup→score→notify
cmd/
  jobfinder/
    main.go             ← time.Ticker 15min, wire all deps
```

---

## Configuration (env vars)

Add to `.env` and `.env.example`:

```
GEMINI_API_KEY=        ← required for AI scoring
```

No additional keys needed — Remotive, Arbeitnow, The Muse are all keyless.

---

## Fly.io Deployment

Run as a second process in the same Fly.io app using `fly.toml` processes:

```toml
[processes]
  server    = "./server"
  jobfinder = "./jobfinder"
```

Both binaries built in the same Dockerfile:

```dockerfile
RUN go build -o server ./cmd/server/
RUN go build -o jobfinder ./cmd/jobfinder/
```

---

## Error Handling

| Error | Behaviour |
|-------|-----------|
| Fetcher API down | Log + skip that source, continue with others |
| Scraper HTML changed | Log parse error + skip that source |
| Gemini rate limit | Exponential backoff, max 3 retries |
| Gemini invalid JSON | Log + skip that job |
| Telegram send fail | Log + retry once after 5s |
| DB unavailable | Log + abort run, retry next tick |

No alerting on single-source failures — only log. If all sources fail, send 1 Telegram error message.

---

## Out of Scope

- No web UI or admin panel
- No job bookmarking / saved jobs
- No user preferences via Telegram commands (profile is hardcoded)
- No LinkedIn scraping (blocks aggressively)
- No email notifications
- Chat session not persisted across bot restarts (in-memory only)

---

## Success Criteria

- Cron runs every 15 min without crashing
- No duplicate job notifications across runs
- AI score correlates with actual job relevance (Flutter/mobile focus)
- Each notification has working apply link
- Runs within Gemini free tier limits
- AI chat responds contextually with full job + CV context
- `/done` cleanly ends chat session
