# Phase 3 — Practice Sessions ❌

## Goal
Allow users to start a practice session, submit answers, and receive scoring.
Reading/Listening: auto-scored server-side. Writing/Speaking: AI-scored (Phase 4).
All protected routes (require JWT).

## Prerequisite
Phase 1 (schema), Phase 2 (exam endpoints) complete.

## Domain Types
Already defined in `internal/domain/practice.go` (done in Phase 1):
- `PracticeSession`, `Answer`

Repository interfaces already defined in `internal/domain/repository.go`:
```go
type PracticeSessionRepository interface {
    Create(s *PracticeSession) error
    GetByID(id uuid.UUID) (*PracticeSession, error)
    ListByUser(userID uuid.UUID) ([]*PracticeSession, error)
    Update(s *PracticeSession) error
    Delete(id uuid.UUID) error
}
type AnswerRepository interface {
    Create(a *Answer) error
    ListBySession(sessionID uuid.UUID) ([]*Answer, error)
    Update(a *Answer) error
    Delete(id uuid.UUID) error
}
```

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

## Scoring Logic
- **Reading / Listening:** compare `user_answer` to `questions.correct` in DB — instant result
- **Writing:** submit to Gemini 2.0 Flash asynchronously — update `answers.ai_score` + `answers.ai_feedback`
- **Speaking:** upload audio → Supabase Storage → Groq Whisper transcription → Gemini score → save transcript + score
- After session `completed`: upsert `user_scores` + upsert `user_levels` + update `user_streaks`

## Files to Create
```
internal/usecase/practice.go
internal/repository/postgres/practice.go
internal/delivery/http/practice_handler.go
```

## Tasks
- [x] Domain structs (`internal/domain/practice.go`) — done in Phase 1
- [x] Repository interfaces — done in Phase 1
- [ ] Implement `repository/postgres/practice.go`
- [ ] Implement `usecase/practice.go` (auto-score reading/listening)
- [ ] Implement `delivery/http/practice_handler.go` with Swagger annotations
- [ ] Register routes in `router.go`
- [ ] Unit tests with fake repo
- [ ] After session complete: update user_scores, user_levels, user_streaks
- [ ] Integration point: call AI scoring usecase (Phase 4) for writing/speaking
