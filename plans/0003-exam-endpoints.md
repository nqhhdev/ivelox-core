# Phase 2 — Exam Endpoints ❌

## Goal
CRUD-read endpoints for exams and sections. No auth required for listing (public content).
Protected routes require valid Supabase JWT.

## Prerequisite
Phase 1 (schema) complete. ✅

## Domain Types
Already defined in `internal/domain/exam.go` (done in Phase 1):
- `Exam`, `Section`, `Question`, `Translation`

Repository interfaces already defined in `internal/domain/repository.go`:
```go
type ExamRepository interface {
    List(skill string) ([]*Exam, error)
    GetByID(id uuid.UUID) (*Exam, error)
}
type SectionRepository interface {
    ListByExam(examID uuid.UUID) ([]*Section, error)
    GetByID(id uuid.UUID) (*Section, error)
}
type QuestionRepository interface {
    ListBySection(sectionID uuid.UUID) ([]*Question, error)
    GetByID(id uuid.UUID) (*Question, error)
}
type TranslationRepository interface {
    Get(sectionID uuid.UUID, lang string) (*Translation, error)
    Upsert(t *Translation) error
}
```

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

## Key Rules
- Correct answers must NOT be returned in `GET /exams` or `GET /sections` responses
- Correct answers only returned in answer verification flow (Phase 3)
- Translation only fetched when `?lang=` param present — no extra DB call otherwise
- All handlers thin: parse → call usecase → respond

## Files to Create
```
internal/usecase/exam.go
internal/repository/postgres/exam.go
internal/delivery/http/exam_handler.go
```

## Tasks
- [x] Domain structs (`internal/domain/exam.go`) — done in Phase 1
- [x] Repository interfaces — done in Phase 1
- [ ] Implement `repository/postgres/exam.go`
- [ ] Implement `usecase/exam.go`
- [ ] Implement `delivery/http/exam_handler.go` with Swagger annotations
- [ ] Register routes in `router.go`
- [ ] Write unit tests with fake exam repo
