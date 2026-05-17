# Phase 7 — Admin Endpoints ❌

## Goal
Protected admin-only routes for content management: create exams, sections, questions, tips, and update translations.
Requires `role = 'admin'` in `profiles` table.

## Prerequisite
Phase 2 (exam endpoints), Phase 6 (tips) complete.

## Admin Middleware
```go
// middleware/admin.go
// After Auth middleware: check profiles.role == 'admin'
// Returns 403 Forbidden if not admin
```

## API Endpoints

### Create exam
```
POST /api/v1/admin/exams
Body: { title, year, source, skill, difficulty }
Response: { exam_id }
```

### Create section
```
POST /api/v1/admin/exams/:examID/sections
Body: { position, title, content, audio_url }
Response: { section_id }
```

### Create question
```
POST /api/v1/admin/sections/:sectionID/questions
Body: { position, type, prompt, options, correct, explanation }
Response: { question_id }
```

### Update translation
```
PUT /api/v1/admin/translations/:id
Body: { content }
Response: { updated_at }
```

### Create / trigger translation (DeepL)
```
POST /api/v1/admin/sections/:sectionID/translate
Body: { lang }
Flow: fetch section content → DeepL API → upsert translations table
Response: { translation_id, lang }
```

### Create tip
```
POST /api/v1/admin/tips
Body: { skill, title, content, band_range }
Response: { tip_id }
```

## DeepL Integration
```
internal/infrastructure/deepl/client.go
```
- Free tier: 500,000 chars/month
- Endpoint: `https://api-free.deepl.com/v2/translate`
- New env var: `DEEPL_API_KEY`

## Admin Domain Interface
```go
type AdminRepository interface {
    CreateExam(ctx context.Context, exam Exam) (string, error)
    CreateSection(ctx context.Context, section Section) (string, error)
    CreateQuestion(ctx context.Context, question Question) (string, error)
    UpsertTranslation(ctx context.Context, sectionID, lang, content string) error
    CreateTip(ctx context.Context, tip Tip) (string, error)
}
```

## Files to Create
```
internal/middleware/admin.go
internal/usecase/admin.go
internal/repository/postgres/admin.go
internal/delivery/http/admin_handler.go
internal/infrastructure/deepl/client.go
```

## Tasks
- [ ] Add `admin.go` middleware (role check)
- [ ] Add admin repository interface + postgres implementation
- [ ] Implement admin usecase
- [ ] Implement admin handler
- [ ] Implement DeepL client
- [ ] Wire DeepL into translate endpoint
- [ ] Add `DEEPL_API_KEY` to config
- [ ] Register admin routes in `router.go` under admin middleware group
- [ ] Unit tests with fake admin repo
