# Phase 4 — AI Scoring (Writing + Speaking) ❌

## Goal
Integrate Gemini 2.0 Flash for writing and speaking scoring.
Integrate Groq Whisper for speaking transcription.
All AI calls are async — results stored in `answers` table.

## Prerequisite
Phase 3 (practice sessions) complete.

## New Environment Variables
```env
GEMINI_API_KEY=
GROQ_API_KEY=
SUPABASE_STORAGE_BUCKET=audio  # for speaking audio uploads
```

## Writing Scoring

### Flow
```
POST /api/v1/practice/answers  (skill=writing)
  → save answer record (status: pending)
  → goroutine: call Gemini 2.0 Flash
  → update answers.ai_score + answers.ai_feedback
```

### Gemini Prompt (Writing)
Evaluate IELTS writing task. Return JSON:
- `band_score` (0–9, 0.5 increments)
- `task_achievement` (score + comment)
- `coherence_cohesion` (score + comment)
- `lexical_resource` (score + comment)
- `grammatical_range` (score + comment)
- `overall_feedback` (2–3 sentences)

### Gemini Model
`gemini-2.0-flash` via REST API (`generativelanguage.googleapis.com`)

## Speaking Scoring

### Flow
```
POST /api/v1/practice/speaking  (multipart audio)
  → upload audio to Supabase Storage (bucket: audio)
  → Groq Whisper API → transcript text
  → Gemini 2.0 Flash → band score + feedback
  → update answers: audio_url, transcript, ai_score, ai_feedback
```

### Groq Whisper
- Model: `whisper-large-v3` (free tier, 7200 audio seconds/day)
- Endpoint: `https://api.groq.com/openai/v1/audio/transcriptions`
- Accept: mp3, mp4, mpeg, mpga, m4a, wav, webm (max 25MB)

### Gemini Prompt (Speaking)
Given IELTS speaking transcript, evaluate:
- `band_score` (0–9)
- `fluency_coherence` (score + comment)
- `lexical_resource` (score + comment)
- `grammatical_range` (score + comment)
- `pronunciation` (comment — cannot score from text, note limitation)
- `overall_feedback`

## Cost Estimate
| Service | Free tier | Paid |
|---|---|---|
| Gemini 2.0 Flash | 1M tokens/day free | $0.075/1M tokens input |
| Groq Whisper | 7,200 sec/day free | $0.111/hour audio |
| Supabase Storage | 1GB free | $0.021/GB |

Estimated cost for 1000 scoring events/month: **< $1**

## Infrastructure Files to Create
```
internal/infrastructure/gemini/client.go    # Gemini REST client
internal/infrastructure/groq/client.go      # Groq Whisper client
internal/usecase/scoring.go                 # ScoreWriting, ScoreSpeaking
```

## Interfaces in Domain
```go
// domain/scoring.go
type AIScorer interface {
    ScoreWriting(ctx context.Context, prompt, essay string) (*WritingScore, error)
    ScoreSpeaking(ctx context.Context, prompt, transcript string) (*SpeakingScore, error)
}

type Transcriber interface {
    Transcribe(ctx context.Context, audioData []byte, mimeType string) (string, error)
}
```

## Tasks
- [ ] Add `AIScorer` and `Transcriber` interfaces to domain
- [ ] Implement `infrastructure/gemini/client.go`
- [ ] Implement `infrastructure/groq/client.go`
- [ ] Implement `usecase/scoring.go`
- [ ] Wire into practice usecase (called after answer submission)
- [ ] Add `GEMINI_API_KEY`, `GROQ_API_KEY` to config
- [ ] Unit tests with mock scorer
- [ ] Load test: ensure goroutine doesn't leak on Gemini timeout
