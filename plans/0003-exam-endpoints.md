# Phase 2 — Exam Endpoints ❌

## Goal
CRUD-read endpoints for exams and sections. No auth required for listing (public content).
Protected routes require valid Supabase JWT.

## Prerequisite
Phase 1 (schema) complete.

## API Endpoints

### List exams
```
GET /api/v1/exams
Query params: skill, year, source, difficulty
Response: { exams: [...], total: int }
```

### Get exam detail
```
GET /api/v1/exams/:id
Response: exam + sections (without question answers)
```

### Get section with questions
```
GET /api/v1/exams/:examID/sections/:sectionID
Response: section + questions (no correct answers exposed) + translation if lang param provided
Query params: lang (optional, e.g. ?lang=vi)
```

## Domain Types to Add (`internal/domain/`)
```go
// exam.go
type Exam struct {
    ID         string
    Title      string
    Year       int
    Source     string
    Skill      string
    Difficulty string
    CreatedAt  time.Time
}

type Section struct {
    ID        string
    ExamID    string
    Position  int
    Title     string
    Content   string
    AudioURL  string
    CreatedAt time.Time
}

type Question struct {
    ID          string
    SectionID   string
    Position    int
    Type        string
    Prompt      string
    Options     []string  // MCQ only
    Explanation string
    // NOTE: correct answer NOT included in read response — only returned after session submit
}
```

## Repository Interface (`internal/domain/repository.go`)
```go
type ExamRepository interface {
    ListExams(ctx context.Context, filter ExamFilter) ([]Exam, error)
    GetExam(ctx context.Context, id string) (*Exam, error)
    GetSection(ctx context.Context, examID, sectionID string) (*Section, error)
    GetQuestions(ctx context.Context, sectionID string) ([]Question, error)
    GetTranslation(ctx context.Context, sectionID, lang string) (string, error)
}
```

## Files to Create
```
internal/domain/exam.go
internal/usecase/exam.go
internal/repository/postgres/exam.go
internal/delivery/http/exam_handler.go
```

## Key Rules
- Correct answers must NOT be returned in `GET /exams` or `GET /sections` responses
- Correct answers only returned in answer verification flow (Phase 3)
- Translation only fetched when `?lang=` param present — no extra DB call otherwise
- All handlers thin: parse → call usecase → respond

## Tasks
- [ ] Add domain structs
- [ ] Add `ExamRepository` interface
- [ ] Implement `repository/postgres/exam.go`
- [ ] Implement `usecase/exam.go`
- [ ] Implement `delivery/http/exam_handler.go`
- [ ] Register routes in `router.go`
- [ ] Write unit tests with fake exam repo
