# Phase 5 — Progress & Dashboard ❌

## Goal
Expose user progress data and smart recommendations for the dashboard.
All protected routes (require JWT).

## Prerequisite
Phase 3 (practice sessions) complete.

## API Endpoints

### Get progress snapshots
```
GET /api/v1/progress
Query params: skill (optional), limit (default 30)
Response: { snapshots: [{ skill, band_score, accuracy, snapshot_at }] }
```

### Get recommendations
```
GET /api/v1/progress/recommendations
Response: { recommendations: [{ exam_id, title, skill, reason }] }
```

## Recommendation Logic
Simple rule-based (no ML needed for v1):
1. Find the skill with the lowest average band score in last 5 snapshots
2. Return 3 unfinished exams of that skill sorted by difficulty asc
3. If all exams completed for that skill → return next hardest skill

## Progress Snapshot Write
Triggered automatically when a practice session is marked `completed`:
- Calculate accuracy = correct answers / total auto-scored answers
- Calculate avg band score from AI-scored answers in session
- Insert row into `progress_snapshots`

## Domain Types (`internal/domain/progress.go`)
```go
type ProgressSnapshot struct {
    ID         string
    UserID     string
    Skill      string
    BandScore  float64
    Accuracy   float64
    SnapshotAt time.Time
}

type Recommendation struct {
    ExamID string
    Title  string
    Skill  string
    Reason string
}
```

## Repository Interface
```go
type ProgressRepository interface {
    CreateSnapshot(ctx context.Context, snapshot ProgressSnapshot) error
    GetSnapshots(ctx context.Context, userID, skill string, limit int) ([]ProgressSnapshot, error)
    GetRecommendations(ctx context.Context, userID string) ([]Recommendation, error)
}
```

## Files to Create
```
internal/domain/progress.go
internal/usecase/progress.go
internal/repository/postgres/progress.go
internal/delivery/http/progress_handler.go
```

## Tasks
- [ ] Add domain structs
- [ ] Add `ProgressRepository` interface
- [ ] Implement postgres progress repository
- [ ] Implement progress usecase (snapshot write + recommendations)
- [ ] Implement progress handler
- [ ] Wire snapshot write into practice session completion
- [ ] Register routes in `router.go`
- [ ] Unit tests with fake repo
