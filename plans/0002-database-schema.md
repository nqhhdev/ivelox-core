# Phase 1 — Database Schema ❌

## Goal
Design and apply all Supabase PostgreSQL migrations needed for exams, practice, progress, and tips.
All tables must have RLS enabled. No application data is fetched by the frontend directly.

## Tables to Create

### exams
```sql
create table public.exams (
  id          uuid primary key default gen_random_uuid(),
  title       text not null,
  year        int,
  source      text,           -- e.g. 'Cambridge 18', 'British Council'
  skill       text not null,  -- 'reading' | 'writing' | 'listening' | 'speaking'
  difficulty  text,           -- 'easy' | 'medium' | 'hard'
  created_at  timestamptz not null default now()
);
```

### sections
Each exam has 1–3 sections (e.g. Reading has 3 passages).
```sql
create table public.sections (
  id          uuid primary key default gen_random_uuid(),
  exam_id     uuid not null references public.exams(id) on delete cascade,
  position    int not null,   -- ordering within exam
  title       text,
  content     text,           -- passage text (reading/listening transcript)
  audio_url   text,           -- Supabase Storage URL (listening only)
  created_at  timestamptz not null default now()
);
```

### questions
```sql
create table public.questions (
  id            uuid primary key default gen_random_uuid(),
  section_id    uuid not null references public.sections(id) on delete cascade,
  position      int not null,
  type          text not null,  -- 'mcq' | 'fill_blank' | 'true_false' | 'matching' | 'short_answer'
  prompt        text not null,
  options       jsonb,          -- for MCQ: ["A","B","C","D"]
  correct       text,           -- correct answer key
  explanation   text,
  created_at    timestamptz not null default now()
);
```

### translations
Pre-computed DeepL translations per section, keyed by language.
```sql
create table public.translations (
  id          uuid primary key default gen_random_uuid(),
  section_id  uuid not null references public.sections(id) on delete cascade,
  lang        text not null,  -- 'vi', 'zh', 'ja'...
  content     text not null,  -- translated passage text
  updated_at  timestamptz not null default now(),
  unique (section_id, lang)
);
```

### practice_sessions
```sql
create table public.practice_sessions (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  exam_id     uuid not null references public.exams(id),
  skill       text not null,
  status      text not null default 'in_progress',  -- 'in_progress' | 'completed' | 'abandoned'
  started_at  timestamptz not null default now(),
  finished_at timestamptz
);
```

### answers
```sql
create table public.answers (
  id              uuid primary key default gen_random_uuid(),
  session_id      uuid not null references public.practice_sessions(id) on delete cascade,
  question_id     uuid references public.questions(id),
  user_answer     text,
  is_correct      boolean,
  ai_score        float,        -- 0–9 band (writing/speaking)
  ai_feedback     text,
  transcript      text,         -- Groq Whisper output (speaking)
  audio_url       text,         -- uploaded audio path (speaking)
  submitted_at    timestamptz not null default now()
);
```

### progress_snapshots
Daily/weekly aggregate for dashboard charts.
```sql
create table public.progress_snapshots (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  skill       text not null,
  band_score  float,
  accuracy    float,           -- 0.0–1.0
  snapshot_at timestamptz not null default now()
);
```

### tips
```sql
create table public.tips (
  id          uuid primary key default gen_random_uuid(),
  skill       text not null,   -- 'reading' | 'writing' | 'listening' | 'speaking' | 'general'
  title       text not null,
  content     text not null,
  band_range  text,            -- e.g. '5-6', '7+' — target audience
  created_at  timestamptz not null default now()
);
```

## RLS Policies
- `exams`, `sections`, `questions`, `translations`, `tips` — public read (authenticated users)
- `practice_sessions` — user can CRUD own rows only
- `answers` — user can CRUD own rows only
- `progress_snapshots` — user can read own rows only (written by backend)

## Indexes
```sql
create index on public.exams(skill);
create index on public.exams(year);
create index on public.sections(exam_id);
create index on public.questions(section_id);
create index on public.practice_sessions(user_id);
create index on public.answers(session_id);
create index on public.progress_snapshots(user_id, skill);
```

## Tasks
- [ ] Apply migration via Supabase MCP or `supabase db push`
- [ ] Seed 1 sample exam (Reading, 3 sections, ~10 questions) for testing
- [ ] Add domain structs in `internal/domain/`: exam.go, practice.go, progress.go, tip.go
- [ ] Add repository interfaces in `internal/domain/repository.go`
