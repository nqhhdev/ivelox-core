# Phase 3 — Practice Sessions ❌

## Goal
Allow users to start a practice session, submit answers, and receive scoring.
Reading/Listening: auto-scored server-side. Writing/Speaking: AI-scored (Phase 4).
All protected routes (require JWT).

## Prerequisite
Phase 1 (schema), Phase 2 (exam endpoints) complete.

## API Endpoints

### Start session
```
POST /api/v1/practice/sessions
Body: { exam_id: string, skill: string }
Response: { session_id: string, started_at: string }
```

### Update session status
```
PATCH /api/v1/practice/sessions/:id
Body: { status: "completed" | "abandoned" }
Response: { session_id, status, finished_at }
```

### Submit answer (reading / listening / writing)
```
POST /api/v1/practice/answers
Body: { session_id, question_id, user_answer }
Response (reading/listening): { is_correct, correct_answer, explanation }
Response (writing): { submitted: true }  — async AI scoring, result in GET session
```

### Submit speaking answer
```
POST /api/v1/practice/speaking
Content-Type: multipart/form-data
Body: { session_id, question_id, audio: <file> }
Flow: upload audio → Groq Whisper → transcript → Gemini score
Response: { submitted: true }  — async, result in GET session
```

### Get session results
```
GET /api/v1/practice/sessions/:id
Response: session + all answers with scores
```

## Domain Types to Add (`internal/domain/practice.go`)
```go
type Session struct {
    ID         string
    UserID     string
    ExamID     string
    Skill      string
    Status     string    // in_progress | completed | abandoned
    StartedAt  time.Time
    FinishedAt *time.Time
}

type Answer struct {
    ID          string
    SessionID   string
    QuestionID  string
    UserAnswer  string
    IsCorrect   *bool
    AIScore     *float64
    AIFeedback  string
    Transcript  string
    AudioURL    string
    SubmittedAt time.Time
}
```

## Repository Interface
```go
type PracticeRepository interface {
    CreateSession(ctx context.Context, session Session) error
    UpdateSession(ctx context.Context, id string, status string, finishedAt *time.Time) error
    GetSession(ctx context.Context, id, userID string) (*Session, error)
    CreateAnswer(ctx context.Context, answer Answer) error
    UpdateAnswerScore(ctx context.Context, id string, score float64, feedback string) error
    GetAnswers(ctx context.Context, sessionID string) ([]Answer, error)
}
```

## Scoring Logic
- **Reading / Listening:** compare `user_answer` to `questions.correct` in DB — instant result
- **Writing:** submit to Gemini 2.0 Flash asynchronously — update `answers.ai_score` + `answers.ai_feedback`
- **Speaking:** upload audio → Supabase Storage → Groq Whisper transcription → Gemini score → save transcript + score
- Progress snapshot written after session `completed` status

## Files to Create
```
internal/domain/practice.go
internal/usecase/practice.go
internal/repository/postgres/practice.go
internal/delivery/http/practice_handler.go
```

## Tasks
- [ ] Add domain structs
- [ ] Add `PracticeRepository` interface
- [ ] Implement postgres practice repository
- [ ] Implement practice usecase (auto-score reading/listening)
- [ ] Implement practice handler
- [ ] Register routes in `router.go`
- [ ] Unit tests with fake repo
- [ ] Integration point: call AI scoring usecase (Phase 4) for writing/speaking
