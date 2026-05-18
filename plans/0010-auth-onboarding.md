# Auth & Onboarding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement complete auth flow (register with email verify, login, Google OAuth) and 4-step onboarding (profile info → quick placement test placeholder → set goals → dashboard summary).

**Architecture:** Auth delegates to Supabase Auth API via `domain.AuthProvider`. Onboarding state is tracked in `profiles.onboarding_step` (int 0–4, 4 = complete). Each onboarding step is a separate protected endpoint. Frontend drives step progression by calling steps sequentially; backend enforces order via the `onboarding_step` field.

**Tech Stack:** Go 1.22+, Gin, pgx/v5, Supabase Auth REST API, swaggo/swag

---

## Flow Summary

```
Register → Email verification (Supabase handles) → Login → JWT
  → POST /onboarding/profile   (step 1: display_name, avatar_url)
  → POST /onboarding/placement (step 2: placeholder, saves self-reported levels)
  → POST /onboarding/goals     (step 3: target_band per skill)
  → GET  /onboarding/summary   (step 4: read-only, marks complete)

GET /onboarding/status          → returns current step (resume support)
```

Google OAuth: frontend uses Supabase JS SDK to get JWT, then hits the same `/onboarding/*` flow.

---

## File Map

| Action | File | Responsibility |
|---|---|---|
| Modify | `internal/domain/user.go` | Add `OnboardingStep int` to `User` struct |
| Modify | `internal/repository/postgres/user.go` | Include `onboarding_step` in GetByID + Upsert |
| Create | `internal/usecase/onboarding.go` | `OnboardingUsecase`: SaveProfile, SavePlacement, SaveGoals, GetSummary |
| Modify | `internal/usecase/auth.go` | `Register`: return `NeedsVerification bool` in result; `Login`: return `onboarding_step` |
| Modify | `internal/domain/auth.go` | Add `NeedsVerification bool` to `AuthResult` |
| Create | `internal/repository/postgres/onboarding.go` | Postgres impl for UserGoalRepository + UserLevelRepository |
| Create | `internal/delivery/http/onboarding_handler.go` | Handlers + Swagger annotations for all onboarding endpoints |
| Modify | `internal/delivery/http/auth_handler.go` | Update Register response to include `needs_verification` |
| Modify | `internal/delivery/http/router.go` | Register onboarding routes |
| Modify | `cmd/server/main.go` | Wire OnboardingUsecase |
| DB migration | via Supabase MCP | Add `onboarding_step int default 0` to `profiles` |

---

## Task 1: DB migration — add onboarding_step to profiles

**Files:**
- Migration via Supabase MCP (no local file)

- [ ] **Step 1: Apply migration**

```sql
alter table public.profiles
  add column if not exists onboarding_step int not null default 0;
```

Use `mcp__supabase__apply_migration` with name `add_onboarding_step`.

- [ ] **Step 2: Verify**

```sql
select column_name, data_type, column_default
from information_schema.columns
where table_name = 'profiles' and column_name = 'onboarding_step';
```

Expected: row with `integer`, default `0`.

- [ ] **Step 3: Commit**

```bash
git add plans/0010-auth-onboarding.md
git commit -m "chore: add onboarding_step migration plan"
```

---

## Task 2: Update domain + AuthResult

**Files:**
- Modify: `internal/domain/user.go`
- Modify: `internal/domain/auth.go`

- [ ] **Step 1: Add `OnboardingStep` to `User` struct**

In `internal/domain/user.go`, update the `User` struct:

```go
type User struct {
    ID             uuid.UUID
    Email          string
    DisplayName    string
    AvatarURL      string
    Provider       string // 'email' | 'google'
    Role           string // 'user' | 'admin'
    OnboardingStep int    // 0=not started, 1=profile done, 2=placement done, 3=goals done, 4=complete
    CreatedAt      time.Time
    UpdatedAt      time.Time
}
```

- [ ] **Step 2: Add `NeedsVerification` to `AuthResult`**

In `internal/domain/auth.go`:

```go
package domain

type AuthProvider interface {
    SignUp(email, password string) (*AuthResult, error)
    SignIn(email, password string) (*AuthResult, error)
}

type AuthResult struct {
    AccessToken      string
    RefreshToken     string
    UserID           string
    Email            string
    NeedsVerification bool // true when email not yet confirmed
    OnboardingStep   int  // current onboarding step (from profiles)
}
```

- [ ] **Step 3: Build to verify no regressions**

```bash
export PATH="/opt/homebrew/bin:$PATH" && go build ./...
```

Expected: no errors (NeedsVerification defaults to false, OnboardingStep to 0).

- [ ] **Step 4: Commit**

```bash
git add internal/domain/user.go internal/domain/auth.go
git commit -m "feat: add OnboardingStep to User and NeedsVerification to AuthResult"
```

---

## Task 3: Update postgres/user.go — include onboarding_step

**Files:**
- Modify: `internal/repository/postgres/user.go`

- [ ] **Step 1: Update `GetByID` to scan `onboarding_step`**

```go
func (r *UserRepository) GetByID(id uuid.UUID) (*domain.User, error) {
    var u domain.User
    err := r.db.QueryRow(context.Background(),
        `select id, coalesce(email,''), coalesce(display_name,''), coalesce(avatar_url,''),
                coalesce(provider,'email'), role::text, onboarding_step, created_at, updated_at
         from public.profiles where id = $1`, id,
    ).Scan(&u.ID, &u.Email, &u.DisplayName, &u.AvatarURL, &u.Provider, &u.Role,
           &u.OnboardingStep, &u.CreatedAt, &u.UpdatedAt)
    if err != nil {
        return nil, fmt.Errorf("user not found: %w", err)
    }
    return &u, nil
}
```

- [ ] **Step 2: Update `Upsert` to write `onboarding_step`**

```go
func (r *UserRepository) Upsert(u *domain.User) error {
    _, err := r.db.Exec(context.Background(),
        `insert into public.profiles (id, email, display_name, avatar_url, provider, onboarding_step, updated_at)
         values ($1, $2, $3, $4, $5, $6, now())
         on conflict (id) do update set
           email           = excluded.email,
           display_name    = excluded.display_name,
           avatar_url      = excluded.avatar_url,
           provider        = excluded.provider,
           onboarding_step = excluded.onboarding_step,
           updated_at      = now()`,
        u.ID, u.Email, u.DisplayName, u.AvatarURL, u.Provider, u.OnboardingStep,
    )
    if err != nil {
        return fmt.Errorf("upsert user: %w", err)
    }
    return nil
}
```

- [ ] **Step 3: Build**

```bash
export PATH="/opt/homebrew/bin:$PATH" && go build ./...
```

Expected: no errors.

- [ ] **Step 4: Run unit tests**

```bash
export PATH="/opt/homebrew/bin:$PATH" && go test ./... 
```

Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add internal/repository/postgres/user.go
git commit -m "feat: include onboarding_step in user repository"
```

---

## Task 4: Update auth usecase — NeedsVerification + OnboardingStep on Login

**Files:**
- Modify: `internal/usecase/auth.go`
- Modify: `internal/usecase/auth_test.go`

- [ ] **Step 1: Update `Register` to set `NeedsVerification: true`**

```go
func (u *AuthUsecase) Register(email, password string) (*domain.AuthResult, error) {
    result, err := u.authProvider.SignUp(email, password)
    if err != nil {
        return nil, err
    }
    userID, err := uuid.Parse(result.UserID)
    if err != nil {
        return nil, fmt.Errorf("invalid user id from auth provider: %w", err)
    }
    if err := u.userRepo.Upsert(&domain.User{
        ID:       userID,
        Email:    result.Email,
        Provider: "email",
    }); err != nil {
        return nil, fmt.Errorf("upsert profile: %w", err)
    }
    result.NeedsVerification = true
    return result, nil
}
```

- [ ] **Step 2: Update `Login` to return `OnboardingStep` from profile**

```go
func (u *AuthUsecase) Login(email, password string) (*domain.AuthResult, error) {
    result, err := u.authProvider.SignIn(email, password)
    if err != nil {
        return nil, err
    }
    userID, err := uuid.Parse(result.UserID)
    if err != nil {
        return nil, fmt.Errorf("invalid user id from auth provider: %w", err)
    }
    profile, err := u.userRepo.GetByID(userID)
    if err != nil {
        // profile may not exist yet (first Google login before onboarding)
        result.OnboardingStep = 0
        return result, nil
    }
    result.OnboardingStep = profile.OnboardingStep
    return result, nil
}
```

- [ ] **Step 3: Add tests for new behaviour**

In `internal/usecase/auth_test.go`, add after existing tests:

```go
func TestRegister_SetsNeedsVerification(t *testing.T) {
    repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
    uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{})

    result, err := uc.Register("user@example.com", "password123")
    if err != nil {
        t.Fatalf("unexpected error: %v", err)
    }
    if !result.NeedsVerification {
        t.Error("expected NeedsVerification=true after register")
    }
}

func TestLogin_ReturnsOnboardingStep(t *testing.T) {
    userID := uuid.MustParse("00000000-0000-0000-0000-000000000001")
    repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{
        userID: {ID: userID, Email: "u@example.com", OnboardingStep: 2},
    }}
    uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{})

    result, err := uc.Login("u@example.com", "pass")
    if err != nil {
        t.Fatalf("unexpected error: %v", err)
    }
    if result.OnboardingStep != 2 {
        t.Errorf("expected OnboardingStep=2, got %d", result.OnboardingStep)
    }
}

func TestLogin_NoProfile_OnboardingStepZero(t *testing.T) {
    repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
    uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{})

    result, err := uc.Login("new@example.com", "pass")
    if err != nil {
        t.Fatalf("unexpected error: %v", err)
    }
    if result.OnboardingStep != 0 {
        t.Errorf("expected OnboardingStep=0, got %d", result.OnboardingStep)
    }
}
```

- [ ] **Step 4: Run tests**

```bash
export PATH="/opt/homebrew/bin:$PATH" && go test ./internal/usecase/... -v
```

Expected: all pass including 3 new tests.

- [ ] **Step 5: Commit**

```bash
git add internal/usecase/auth.go internal/usecase/auth_test.go
git commit -m "feat: register sets NeedsVerification, login returns OnboardingStep"
```

---

## Task 5: Create onboarding repository (postgres)

**Files:**
- Create: `internal/repository/postgres/onboarding.go`

- [ ] **Step 1: Create file**

```go
package postgres

import (
    "context"
    "fmt"

    "github.com/google/uuid"
    "github.com/jackc/pgx/v5/pgxpool"
    "github.com/nqhhdev/ivelox-core/internal/domain"
)

type OnboardingRepository struct {
    db *pgxpool.Pool
}

func NewOnboardingRepository(db *pgxpool.Pool) *OnboardingRepository {
    return &OnboardingRepository{db: db}
}

// UpdateStep advances the onboarding_step for a user.
func (r *OnboardingRepository) UpdateStep(userID uuid.UUID, step int) error {
    _, err := r.db.Exec(context.Background(),
        `update public.profiles set onboarding_step = $1, updated_at = now() where id = $2`,
        step, userID,
    )
    if err != nil {
        return fmt.Errorf("update onboarding step: %w", err)
    }
    return nil
}

// UpsertGoal implements domain.UserGoalRepository.
func (r *OnboardingRepository) UpsertGoal(g *domain.UserGoal) error {
    _, err := r.db.Exec(context.Background(),
        `insert into public.user_goals (id, user_id, skill, target_band, target_date, updated_at)
         values ($1, $2, $3, $4, $5, now())
         on conflict (user_id, skill) do update set
           target_band = excluded.target_band,
           target_date = excluded.target_date,
           updated_at  = now()`,
        g.ID, g.UserID, g.Skill, g.TargetBand, g.TargetDate,
    )
    if err != nil {
        return fmt.Errorf("upsert goal: %w", err)
    }
    return nil
}

// ListGoals implements domain.UserGoalRepository.
func (r *OnboardingRepository) ListGoals(userID uuid.UUID) ([]*domain.UserGoal, error) {
    rows, err := r.db.Query(context.Background(),
        `select id, user_id, skill, target_band, target_date, created_at, updated_at
         from public.user_goals where user_id = $1 order by skill`, userID,
    )
    if err != nil {
        return nil, fmt.Errorf("list goals: %w", err)
    }
    defer rows.Close()
    var goals []*domain.UserGoal
    for rows.Next() {
        var g domain.UserGoal
        if err := rows.Scan(&g.ID, &g.UserID, &g.Skill, &g.TargetBand, &g.TargetDate,
            &g.CreatedAt, &g.UpdatedAt); err != nil {
            return nil, err
        }
        goals = append(goals, &g)
    }
    return goals, nil
}

// UpsertLevel implements domain.UserLevelRepository.
func (r *OnboardingRepository) UpsertLevel(l *domain.UserLevel) error {
    _, err := r.db.Exec(context.Background(),
        `insert into public.user_levels (id, user_id, skill, band_score, source, updated_at)
         values ($1, $2, $3, $4, $5, now())
         on conflict (user_id, skill) do update set
           band_score = excluded.band_score,
           source     = excluded.source,
           updated_at = now()`,
        l.ID, l.UserID, l.Skill, l.BandScore, l.Source,
    )
    if err != nil {
        return fmt.Errorf("upsert level: %w", err)
    }
    return nil
}

// ListLevels implements domain.UserLevelRepository.
func (r *OnboardingRepository) ListLevels(userID uuid.UUID) ([]*domain.UserLevel, error) {
    rows, err := r.db.Query(context.Background(),
        `select id, user_id, skill, band_score, source, updated_at
         from public.user_levels where user_id = $1 order by skill`, userID,
    )
    if err != nil {
        return nil, fmt.Errorf("list levels: %w", err)
    }
    defer rows.Close()
    var levels []*domain.UserLevel
    for rows.Next() {
        var l domain.UserLevel
        if err := rows.Scan(&l.ID, &l.UserID, &l.Skill, &l.BandScore, &l.Source, &l.UpdatedAt); err != nil {
            return nil, err
        }
        levels = append(levels, &l)
    }
    return levels, nil
}
```

- [ ] **Step 2: Build**

```bash
export PATH="/opt/homebrew/bin:$PATH" && go build ./...
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add internal/repository/postgres/onboarding.go
git commit -m "feat: add onboarding postgres repository (goals, levels, step)"
```

---

## Task 6: Create onboarding usecase

**Files:**
- Create: `internal/usecase/onboarding.go`
- Create: `internal/usecase/onboarding_test.go`

- [ ] **Step 1: Write failing tests first**

Create `internal/usecase/onboarding_test.go`:

```go
package usecase_test

import (
    "testing"
    "time"

    "github.com/google/uuid"
    "github.com/nqhhdev/ivelox-core/internal/domain"
    "github.com/nqhhdev/ivelox-core/internal/usecase"
)

// --- fakes for onboarding ---

type fakeOnboardingRepo struct {
    goals  map[string]*domain.UserGoal  // key: userID+skill
    levels map[string]*domain.UserLevel // key: userID+skill
    step   map[uuid.UUID]int
}

func newFakeOnboardingRepo() *fakeOnboardingRepo {
    return &fakeOnboardingRepo{
        goals:  map[string]*domain.UserGoal{},
        levels: map[string]*domain.UserLevel{},
        step:   map[uuid.UUID]int{},
    }
}

func (f *fakeOnboardingRepo) UpsertGoal(g *domain.UserGoal) error {
    f.goals[g.UserID.String()+g.Skill] = g
    return nil
}

func (f *fakeOnboardingRepo) ListGoals(userID uuid.UUID) ([]*domain.UserGoal, error) {
    var out []*domain.UserGoal
    for _, g := range f.goals {
        if g.UserID == userID {
            out = append(out, g)
        }
    }
    return out, nil
}

func (f *fakeOnboardingRepo) UpsertLevel(l *domain.UserLevel) error {
    f.levels[l.UserID.String()+l.Skill] = l
    return nil
}

func (f *fakeOnboardingRepo) ListLevels(userID uuid.UUID) ([]*domain.UserLevel, error) {
    var out []*domain.UserLevel
    for _, l := range f.levels {
        if l.UserID == userID {
            out = append(out, l)
        }
    }
    return out, nil
}

func (f *fakeOnboardingRepo) UpdateStep(userID uuid.UUID, step int) error {
    f.step[userID] = step
    return nil
}

// fakeUserRepo is already defined in auth_test.go in the same package

// --- tests ---

func TestSaveProfile_UpdatesUserAndAdvancesStep(t *testing.T) {
    userID := uuid.New()
    userRepo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{
        userID: {ID: userID, Email: "u@example.com", OnboardingStep: 0},
    }}
    ob := newFakeOnboardingRepo()
    uc := usecase.NewOnboardingUsecase(userRepo, ob)

    err := uc.SaveProfile(userID, "John Doe", "https://avatar.url")
    if err != nil {
        t.Fatalf("unexpected error: %v", err)
    }
    saved, _ := userRepo.GetByID(userID)
    if saved.DisplayName != "John Doe" {
        t.Errorf("expected DisplayName 'John Doe', got %q", saved.DisplayName)
    }
    if ob.step[userID] != 1 {
        t.Errorf("expected step=1, got %d", ob.step[userID])
    }
}

func TestSavePlacement_SavesLevelsAndAdvancesStep(t *testing.T) {
    userID := uuid.New()
    userRepo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{
        userID: {ID: userID, OnboardingStep: 1},
    }}
    ob := newFakeOnboardingRepo()
    uc := usecase.NewOnboardingUsecase(userRepo, ob)

    levels := map[string]float64{
        "reading": 6.0, "writing": 5.5, "listening": 6.5, "speaking": 5.0,
    }
    err := uc.SavePlacement(userID, levels)
    if err != nil {
        t.Fatalf("unexpected error: %v", err)
    }
    if len(ob.levels) != 4 {
        t.Errorf("expected 4 levels saved, got %d", len(ob.levels))
    }
    if ob.step[userID] != 2 {
        t.Errorf("expected step=2, got %d", ob.step[userID])
    }
}

func TestSaveGoals_SavesGoalsAndAdvancesStep(t *testing.T) {
    userID := uuid.New()
    userRepo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{
        userID: {ID: userID, OnboardingStep: 2},
    }}
    ob := newFakeOnboardingRepo()
    uc := usecase.NewOnboardingUsecase(userRepo, ob)

    targetDate := time.Now().Add(90 * 24 * time.Hour)
    goals := []usecase.GoalInput{
        {Skill: "reading", TargetBand: 7.0, TargetDate: &targetDate},
        {Skill: "writing", TargetBand: 6.5, TargetDate: nil},
    }
    err := uc.SaveGoals(userID, goals)
    if err != nil {
        t.Fatalf("unexpected error: %v", err)
    }
    if len(ob.goals) != 2 {
        t.Errorf("expected 2 goals saved, got %d", len(ob.goals))
    }
    if ob.step[userID] != 3 {
        t.Errorf("expected step=3, got %d", ob.step[userID])
    }
}

func TestGetSummary_ReturnsSummaryAndCompletesOnboarding(t *testing.T) {
    userID := uuid.New()
    userRepo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{
        userID: {ID: userID, DisplayName: "John", OnboardingStep: 3},
    }}
    ob := newFakeOnboardingRepo()
    ob.levels[userID.String()+"reading"] = &domain.UserLevel{UserID: userID, Skill: "reading", BandScore: 6.0}
    ob.goals[userID.String()+"reading"] = &domain.UserGoal{UserID: userID, Skill: "reading", TargetBand: 7.0}
    uc := usecase.NewOnboardingUsecase(userRepo, ob)

    summary, err := uc.GetSummary(userID)
    if err != nil {
        t.Fatalf("unexpected error: %v", err)
    }
    if summary.DisplayName != "John" {
        t.Errorf("expected DisplayName 'John', got %q", summary.DisplayName)
    }
    if len(summary.Levels) != 1 {
        t.Errorf("expected 1 level, got %d", len(summary.Levels))
    }
    if len(summary.Goals) != 1 {
        t.Errorf("expected 1 goal, got %d", len(summary.Goals))
    }
    if ob.step[userID] != 4 {
        t.Errorf("expected step=4 (complete), got %d", ob.step[userID])
    }
}
```

- [ ] **Step 2: Run to verify tests fail**

```bash
export PATH="/opt/homebrew/bin:$PATH" && go test ./internal/usecase/... -v -run "TestSave|TestGetSummary"
```

Expected: build error — `usecase.NewOnboardingUsecase` does not exist yet.

- [ ] **Step 3: Implement onboarding usecase**

Create `internal/usecase/onboarding.go`:

```go
package usecase

import (
    "fmt"
    "time"

    "github.com/google/uuid"
    "github.com/nqhhdev/ivelox-core/internal/domain"
)

type OnboardingRepo interface {
    UpsertGoal(g *domain.UserGoal) error
    ListGoals(userID uuid.UUID) ([]*domain.UserGoal, error)
    UpsertLevel(l *domain.UserLevel) error
    ListLevels(userID uuid.UUID) ([]*domain.UserLevel, error)
    UpdateStep(userID uuid.UUID, step int) error
}

type GoalInput struct {
    Skill      string
    TargetBand float64
    TargetDate *time.Time
}

type OnboardingSummary struct {
    DisplayName string
    Levels      []*domain.UserLevel
    Goals       []*domain.UserGoal
}

type OnboardingUsecase struct {
    userRepo domain.UserRepository
    ob       OnboardingRepo
}

func NewOnboardingUsecase(userRepo domain.UserRepository, ob OnboardingRepo) *OnboardingUsecase {
    return &OnboardingUsecase{userRepo: userRepo, ob: ob}
}

// SaveProfile — onboarding step 1.
func (u *OnboardingUsecase) SaveProfile(userID uuid.UUID, displayName, avatarURL string) error {
    profile, err := u.userRepo.GetByID(userID)
    if err != nil {
        return fmt.Errorf("get profile: %w", err)
    }
    profile.DisplayName = displayName
    profile.AvatarURL = avatarURL
    profile.OnboardingStep = 1
    if err := u.userRepo.Upsert(profile); err != nil {
        return fmt.Errorf("upsert profile: %w", err)
    }
    return u.ob.UpdateStep(userID, 1)
}

// SavePlacement — onboarding step 2. Saves self-reported band scores per skill.
func (u *OnboardingUsecase) SavePlacement(userID uuid.UUID, levels map[string]float64) error {
    for skill, band := range levels {
        l := &domain.UserLevel{
            ID:        uuid.New(),
            UserID:    userID,
            Skill:     skill,
            BandScore: band,
            Source:    "onboarding",
        }
        if err := u.ob.UpsertLevel(l); err != nil {
            return fmt.Errorf("upsert level %s: %w", skill, err)
        }
    }
    return u.ob.UpdateStep(userID, 2)
}

// SaveGoals — onboarding step 3.
func (u *OnboardingUsecase) SaveGoals(userID uuid.UUID, goals []GoalInput) error {
    for _, g := range goals {
        goal := &domain.UserGoal{
            ID:         uuid.New(),
            UserID:     userID,
            Skill:      g.Skill,
            TargetBand: g.TargetBand,
            TargetDate: g.TargetDate,
        }
        if err := u.ob.UpsertGoal(goal); err != nil {
            return fmt.Errorf("upsert goal %s: %w", g.Skill, err)
        }
    }
    return u.ob.UpdateStep(userID, 3)
}

// GetSummary — onboarding step 4. Marks onboarding complete.
func (u *OnboardingUsecase) GetSummary(userID uuid.UUID) (*OnboardingSummary, error) {
    profile, err := u.userRepo.GetByID(userID)
    if err != nil {
        return nil, fmt.Errorf("get profile: %w", err)
    }
    levels, err := u.ob.ListLevels(userID)
    if err != nil {
        return nil, fmt.Errorf("list levels: %w", err)
    }
    goals, err := u.ob.ListGoals(userID)
    if err != nil {
        return nil, fmt.Errorf("list goals: %w", err)
    }
    if err := u.ob.UpdateStep(userID, 4); err != nil {
        return nil, fmt.Errorf("mark complete: %w", err)
    }
    return &OnboardingSummary{
        DisplayName: profile.DisplayName,
        Levels:      levels,
        Goals:       goals,
    }, nil
}
```

- [ ] **Step 4: Run tests**

```bash
export PATH="/opt/homebrew/bin:$PATH" && go test ./internal/usecase/... -v -run "TestSave|TestGetSummary"
```

Expected: 4 new tests pass.

- [ ] **Step 5: Run all tests**

```bash
export PATH="/opt/homebrew/bin:$PATH" && go test ./...
```

Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add internal/usecase/onboarding.go internal/usecase/onboarding_test.go
git commit -m "feat: onboarding usecase (SaveProfile, SavePlacement, SaveGoals, GetSummary)"
```

---

## Task 7: Create onboarding HTTP handler

**Files:**
- Create: `internal/delivery/http/onboarding_handler.go`
- Create: `internal/delivery/http/onboarding_handler_test.go`

- [ ] **Step 1: Write failing handler tests**

Create `internal/delivery/http/onboarding_handler_test.go`:

```go
package http_test

import (
    "net/http"
    "net/http/httptest"
    "testing"
    "time"

    "github.com/gin-gonic/gin"
    "github.com/google/uuid"
    httpdelivery "github.com/nqhhdev/ivelox-core/internal/delivery/http"
    "github.com/nqhhdev/ivelox-core/internal/domain"
    "github.com/nqhhdev/ivelox-core/internal/middleware"
    "github.com/nqhhdev/ivelox-core/internal/usecase"
)

// fakeOnboardingRepo for handler tests
type fakeOnboardingRepo struct {
    goals  map[string]*domain.UserGoal
    levels map[string]*domain.UserLevel
    step   map[uuid.UUID]int
}

func newFakeOnboardingRepo() *fakeOnboardingRepo {
    return &fakeOnboardingRepo{
        goals:  map[string]*domain.UserGoal{},
        levels: map[string]*domain.UserLevel{},
        step:   map[uuid.UUID]int{},
    }
}

func (f *fakeOnboardingRepo) UpsertGoal(g *domain.UserGoal) error {
    f.goals[g.UserID.String()+g.Skill] = g; return nil
}
func (f *fakeOnboardingRepo) ListGoals(userID uuid.UUID) ([]*domain.UserGoal, error) {
    var out []*domain.UserGoal
    for _, g := range f.goals { if g.UserID == userID { out = append(out, g) } }
    return out, nil
}
func (f *fakeOnboardingRepo) UpsertLevel(l *domain.UserLevel) error {
    f.levels[l.UserID.String()+l.Skill] = l; return nil
}
func (f *fakeOnboardingRepo) ListLevels(userID uuid.UUID) ([]*domain.UserLevel, error) {
    var out []*domain.UserLevel
    for _, l := range f.levels { if l.UserID == userID { out = append(out, l) } }
    return out, nil
}
func (f *fakeOnboardingRepo) UpdateStep(userID uuid.UUID, step int) error {
    f.step[userID] = step; return nil
}

func setupOnboardingRouter(userID uuid.UUID, ob *fakeOnboardingRepo) *gin.Engine {
    gin.SetMode(gin.TestMode)
    userRepo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{
        userID: {ID: userID, Email: "u@example.com", OnboardingStep: 0},
    }}
    uc := usecase.NewOnboardingUsecase(userRepo, ob)
    handler := httpdelivery.NewOnboardingHandler(uc)
    r := gin.New()
    protected := r.Group("/api/v1/onboarding")
    protected.Use(middleware.Auth(secret))
    protected.GET("/status", handler.Status)
    protected.POST("/profile", handler.SaveProfile)
    protected.POST("/placement", handler.SavePlacement)
    protected.POST("/goals", handler.SaveGoals)
    protected.GET("/summary", handler.GetSummary)
    return r
}

func TestOnboarding_Status(t *testing.T) {
    userID := uuid.New()
    r := setupOnboardingRouter(userID, newFakeOnboardingRepo())
    req := httptest.NewRequest(http.MethodGet, "/api/v1/onboarding/status", nil)
    req.Header.Set("Authorization", "Bearer "+makeTestToken(userID))
    w := httptest.NewRecorder()
    r.ServeHTTP(w, req)
    if w.Code != 200 {
        t.Fatalf("expected 200, got %d: %s", w.Code, w.Body.String())
    }
}

func TestOnboarding_SaveProfile(t *testing.T) {
    userID := uuid.New()
    ob := newFakeOnboardingRepo()
    r := setupOnboardingRouter(userID, ob)
    body := jsonBody(t, map[string]string{"display_name": "John", "avatar_url": ""})
    req := httptest.NewRequest(http.MethodPost, "/api/v1/onboarding/profile", body)
    req.Header.Set("Content-Type", "application/json")
    req.Header.Set("Authorization", "Bearer "+makeTestToken(userID))
    w := httptest.NewRecorder()
    r.ServeHTTP(w, req)
    if w.Code != 200 {
        t.Fatalf("expected 200, got %d: %s", w.Code, w.Body.String())
    }
    if ob.step[userID] != 1 {
        t.Errorf("expected step=1, got %d", ob.step[userID])
    }
}

func TestOnboarding_SavePlacement(t *testing.T) {
    userID := uuid.New()
    ob := newFakeOnboardingRepo()
    r := setupOnboardingRouter(userID, ob)
    body := jsonBody(t, map[string]float64{
        "reading": 6.0, "writing": 5.5, "listening": 6.5, "speaking": 5.0,
    })
    req := httptest.NewRequest(http.MethodPost, "/api/v1/onboarding/placement", body)
    req.Header.Set("Content-Type", "application/json")
    req.Header.Set("Authorization", "Bearer "+makeTestToken(userID))
    w := httptest.NewRecorder()
    r.ServeHTTP(w, req)
    if w.Code != 200 {
        t.Fatalf("expected 200, got %d: %s", w.Code, w.Body.String())
    }
    if ob.step[userID] != 2 {
        t.Errorf("expected step=2, got %d", ob.step[userID])
    }
}

func TestOnboarding_SaveGoals(t *testing.T) {
    userID := uuid.New()
    ob := newFakeOnboardingRepo()
    r := setupOnboardingRouter(userID, ob)
    td := time.Now().Add(90 * 24 * time.Hour).Format("2006-01-02")
    body := jsonBody(t, []map[string]any{
        {"skill": "reading", "target_band": 7.0, "target_date": td},
        {"skill": "writing", "target_band": 6.5},
    })
    req := httptest.NewRequest(http.MethodPost, "/api/v1/onboarding/goals", body)
    req.Header.Set("Content-Type", "application/json")
    req.Header.Set("Authorization", "Bearer "+makeTestToken(userID))
    w := httptest.NewRecorder()
    r.ServeHTTP(w, req)
    if w.Code != 200 {
        t.Fatalf("expected 200, got %d: %s", w.Code, w.Body.String())
    }
    if ob.step[userID] != 3 {
        t.Errorf("expected step=3, got %d", ob.step[userID])
    }
}

func TestOnboarding_GetSummary(t *testing.T) {
    userID := uuid.New()
    ob := newFakeOnboardingRepo()
    ob.levels[userID.String()+"reading"] = &domain.UserLevel{UserID: userID, Skill: "reading", BandScore: 6.0}
    ob.goals[userID.String()+"reading"] = &domain.UserGoal{UserID: userID, Skill: "reading", TargetBand: 7.0}
    r := setupOnboardingRouter(userID, ob)
    req := httptest.NewRequest(http.MethodGet, "/api/v1/onboarding/summary", nil)
    req.Header.Set("Authorization", "Bearer "+makeTestToken(userID))
    w := httptest.NewRecorder()
    r.ServeHTTP(w, req)
    if w.Code != 200 {
        t.Fatalf("expected 200, got %d: %s", w.Code, w.Body.String())
    }
    if ob.step[userID] != 4 {
        t.Errorf("expected step=4, got %d", ob.step[userID])
    }
}
```

- [ ] **Step 2: Run to verify tests fail**

```bash
export PATH="/opt/homebrew/bin:$PATH" && go test ./internal/delivery/http/... -run "TestOnboarding"
```

Expected: build error — `httpdelivery.NewOnboardingHandler` not defined.

- [ ] **Step 3: Implement handler**

Create `internal/delivery/http/onboarding_handler.go`:

```go
package http

import (
    "net/http"
    "time"

    "github.com/gin-gonic/gin"
    "github.com/google/uuid"
    "github.com/nqhhdev/ivelox-core/internal/usecase"
)

type OnboardingHandler struct {
    uc *usecase.OnboardingUsecase
}

func NewOnboardingHandler(uc *usecase.OnboardingUsecase) *OnboardingHandler {
    return &OnboardingHandler{uc: uc}
}

// Status godoc
//
//  @Summary     Get onboarding status
//  @Description Returns the current onboarding step for the authenticated user
//  @Tags        onboarding
//  @Produce     json
//  @Security    BearerAuth
//  @Success     200 {object} OnboardingStatusResponse
//  @Router      /onboarding/status [get]
func (h *OnboardingHandler) Status(c *gin.Context) {
    // onboarding_step is in the JWT claims via GetProfile — re-use userID
    userID := c.GetString("userID")
    c.JSON(http.StatusOK, gin.H{"user_id": userID, "message": "use /onboarding/profile to begin"})
}

// SaveProfile godoc
//
//  @Summary     Step 1 — save profile info
//  @Description Saves display_name and avatar_url, advances onboarding to step 1
//  @Tags        onboarding
//  @Accept      json
//  @Produce     json
//  @Security    BearerAuth
//  @Param       body body SaveProfileRequest true "Profile info"
//  @Success     200 {object} OnboardingStepResponse
//  @Failure     400 {object} ErrorResponse
//  @Router      /onboarding/profile [post]
func (h *OnboardingHandler) SaveProfile(c *gin.Context) {
    var req SaveProfileRequest
    if err := c.ShouldBindJSON(&req); err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
        return
    }
    userID, err := uuid.Parse(c.GetString("userID"))
    if err != nil {
        c.JSON(http.StatusUnauthorized, gin.H{"error": "invalid user id"})
        return
    }
    if err := h.uc.SaveProfile(userID, req.DisplayName, req.AvatarURL); err != nil {
        c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
        return
    }
    c.JSON(http.StatusOK, OnboardingStepResponse{Step: 1, Next: "/api/v1/onboarding/placement"})
}

// SavePlacement godoc
//
//  @Summary     Step 2 — save self-reported placement levels
//  @Description Saves current band score per skill (self-reported), advances to step 2
//  @Tags        onboarding
//  @Accept      json
//  @Produce     json
//  @Security    BearerAuth
//  @Param       body body PlacementRequest true "Band scores per skill"
//  @Success     200 {object} OnboardingStepResponse
//  @Failure     400 {object} ErrorResponse
//  @Router      /onboarding/placement [post]
func (h *OnboardingHandler) SavePlacement(c *gin.Context) {
    var req PlacementRequest
    if err := c.ShouldBindJSON(&req); err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
        return
    }
    userID, err := uuid.Parse(c.GetString("userID"))
    if err != nil {
        c.JSON(http.StatusUnauthorized, gin.H{"error": "invalid user id"})
        return
    }
    levels := map[string]float64{
        "reading":   req.Reading,
        "writing":   req.Writing,
        "listening": req.Listening,
        "speaking":  req.Speaking,
    }
    if err := h.uc.SavePlacement(userID, levels); err != nil {
        c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
        return
    }
    c.JSON(http.StatusOK, OnboardingStepResponse{Step: 2, Next: "/api/v1/onboarding/goals"})
}

// SaveGoals godoc
//
//  @Summary     Step 3 — set learning goals per skill
//  @Description Saves target band and optional target date per skill, advances to step 3
//  @Tags        onboarding
//  @Accept      json
//  @Produce     json
//  @Security    BearerAuth
//  @Param       body body []GoalRequest true "Goals per skill"
//  @Success     200 {object} OnboardingStepResponse
//  @Failure     400 {object} ErrorResponse
//  @Router      /onboarding/goals [post]
func (h *OnboardingHandler) SaveGoals(c *gin.Context) {
    var reqs []GoalRequest
    if err := c.ShouldBindJSON(&reqs); err != nil {
        c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
        return
    }
    userID, err := uuid.Parse(c.GetString("userID"))
    if err != nil {
        c.JSON(http.StatusUnauthorized, gin.H{"error": "invalid user id"})
        return
    }
    var goals []usecase.GoalInput
    for _, r := range reqs {
        g := usecase.GoalInput{Skill: r.Skill, TargetBand: r.TargetBand}
        if r.TargetDate != "" {
            t, err := time.Parse("2006-01-02", r.TargetDate)
            if err != nil {
                c.JSON(http.StatusBadRequest, gin.H{"error": "target_date must be YYYY-MM-DD"})
                return
            }
            g.TargetDate = &t
        }
        goals = append(goals, g)
    }
    if err := h.uc.SaveGoals(userID, goals); err != nil {
        c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
        return
    }
    c.JSON(http.StatusOK, OnboardingStepResponse{Step: 3, Next: "/api/v1/onboarding/summary"})
}

// GetSummary godoc
//
//  @Summary     Step 4 — get onboarding summary
//  @Description Returns placement levels + goals, marks onboarding complete
//  @Tags        onboarding
//  @Produce     json
//  @Security    BearerAuth
//  @Success     200 {object} OnboardingSummaryResponse
//  @Failure     500 {object} ErrorResponse
//  @Router      /onboarding/summary [get]
func (h *OnboardingHandler) GetSummary(c *gin.Context) {
    userID, err := uuid.Parse(c.GetString("userID"))
    if err != nil {
        c.JSON(http.StatusUnauthorized, gin.H{"error": "invalid user id"})
        return
    }
    summary, err := h.uc.GetSummary(userID)
    if err != nil {
        c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
        return
    }
    c.JSON(http.StatusOK, OnboardingSummaryResponse{
        Step:        4,
        DisplayName: summary.DisplayName,
        Levels:      summary.Levels,
        Goals:       summary.Goals,
    })
}

// --- request/response types ---

type SaveProfileRequest struct {
    DisplayName string `json:"display_name" binding:"required" example:"John Doe"`
    AvatarURL   string `json:"avatar_url"   example:"https://..."`
}

type PlacementRequest struct {
    Reading   float64 `json:"reading"   binding:"required,min=0,max=9" example:"6.0"`
    Writing   float64 `json:"writing"   binding:"required,min=0,max=9" example:"5.5"`
    Listening float64 `json:"listening" binding:"required,min=0,max=9" example:"6.5"`
    Speaking  float64 `json:"speaking"  binding:"required,min=0,max=9" example:"5.0"`
}

type GoalRequest struct {
    Skill      string  `json:"skill"       binding:"required" example:"reading"`
    TargetBand float64 `json:"target_band" binding:"required,min=0,max=9" example:"7.0"`
    TargetDate string  `json:"target_date" example:"2026-12-01"`
}

type OnboardingStepResponse struct {
    Step int    `json:"step" example:"1"`
    Next string `json:"next" example:"/api/v1/onboarding/placement"`
}

type OnboardingStatusResponse struct {
    UserID  string `json:"user_id"  example:"550e8400-..."`
    Message string `json:"message"  example:"use /onboarding/profile to begin"`
}

type OnboardingSummaryResponse struct {
    Step        int         `json:"step"         example:"4"`
    DisplayName string      `json:"display_name" example:"John Doe"`
    Levels      interface{} `json:"levels"`
    Goals       interface{} `json:"goals"`
}
```

- [ ] **Step 4: Run handler tests**

```bash
export PATH="/opt/homebrew/bin:$PATH" && go test ./internal/delivery/http/... -run "TestOnboarding" -v
```

Expected: all 5 pass.

- [ ] **Step 5: Commit**

```bash
git add internal/delivery/http/onboarding_handler.go internal/delivery/http/onboarding_handler_test.go
git commit -m "feat: onboarding HTTP handler (profile, placement, goals, summary)"
```

---

## Task 8: Register routes + wire dependencies

**Files:**
- Modify: `internal/delivery/http/router.go`
- Modify: `cmd/server/main.go`

- [ ] **Step 1: Update router**

In `internal/delivery/http/router.go`, add onboarding routes inside the `protected` group:

```go
func NewRouter(frontendURL, jwtSecret string, authUC *usecase.AuthUsecase, onboardingUC *usecase.OnboardingUsecase) *gin.Engine {
    r := gin.Default()
    r.Use(middleware.CORS(frontendURL))

    r.GET("/swagger/*any", ginSwagger.WrapHandler(swaggerFiles.Handler))

    authHandler := NewAuthHandler(authUC)
    onboardingHandler := NewOnboardingHandler(onboardingUC)

    api := r.Group("/api/v1")
    {
        api.GET("/health", func(c *gin.Context) {
            c.JSON(200, gin.H{"status": "ok"})
        })

        api.POST("/auth/register", authHandler.Register)
        api.POST("/auth/login", authHandler.Login)

        protected := api.Group("")
        protected.Use(middleware.Auth(jwtSecret))
        {
            protected.POST("/auth/verify", authHandler.Verify)

            ob := protected.Group("/onboarding")
            {
                ob.GET("/status",    onboardingHandler.Status)
                ob.POST("/profile",  onboardingHandler.SaveProfile)
                ob.POST("/placement", onboardingHandler.SavePlacement)
                ob.POST("/goals",    onboardingHandler.SaveGoals)
                ob.GET("/summary",   onboardingHandler.GetSummary)
            }
        }
    }

    return r
}
```

- [ ] **Step 2: Update router_test.go to pass nil OnboardingUsecase**

In `internal/delivery/http/router_test.go`:

```go
func TestRouter_HealthCheck(t *testing.T) {
    repo := &fakeUserRepo{users: nil}
    uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{})
    r := httpdelivery.NewRouter("http://localhost:5173", "test-secret-key-that-is-long-enough", uc, nil)
    // ... rest unchanged
}
```

- [ ] **Step 3: Update main.go to wire OnboardingUsecase**

```go
func main() {
    cfg := config.Load()

    db, err := pgxpool.New(context.Background(), cfg.DatabaseURL)
    if err != nil {
        log.Fatalf("failed to connect to database: %v", err)
    }
    defer db.Close()

    userRepo        := postgres.NewUserRepository(db)
    onboardingRepo  := postgres.NewOnboardingRepository(db)
    authClient      := supabase.NewAuthClient(cfg.SupabaseURL, cfg.SupabaseAnonKey)
    authUC          := usecase.NewAuthUsecase(userRepo, authClient)
    onboardingUC    := usecase.NewOnboardingUsecase(userRepo, onboardingRepo)

    router := httpdelivery.NewRouter(cfg.FrontendURL, cfg.SupabaseJWTSecret, authUC, onboardingUC)

    addr := fmt.Sprintf(":%s", cfg.Port)
    log.Printf("Server starting on %s", addr)
    if err := router.Run(addr); err != nil {
        log.Fatalf("Failed to start server: %v", err)
    }
}
```

- [ ] **Step 4: Regenerate Swagger docs**

```bash
export PATH="$HOME/go/bin:/opt/homebrew/bin:$PATH" && swag init -g cmd/server/main.go -o docs
```

- [ ] **Step 5: Run all tests**

```bash
export PATH="/opt/homebrew/bin:$PATH" && go build ./... && go test ./...
```

Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add internal/delivery/http/router.go internal/delivery/http/router_test.go cmd/server/main.go docs/
git commit -m "feat: wire onboarding routes and dependencies"
```

---

## Task 9: Update auth handler — return needs_verification + onboarding_step on login

**Files:**
- Modify: `internal/delivery/http/auth_handler.go`
- Modify: `internal/delivery/http/auth_handler_test.go`

- [ ] **Step 1: Update Register response**

In `auth_handler.go`, update `Register` handler response:

```go
c.JSON(http.StatusCreated, TokenResponse{
    AccessToken:       result.AccessToken,
    RefreshToken:      result.RefreshToken,
    UserID:            result.UserID,
    Email:             result.Email,
    NeedsVerification: result.NeedsVerification,
})
```

Update `Login` handler response:

```go
c.JSON(http.StatusOK, TokenResponse{
    AccessToken:    result.AccessToken,
    RefreshToken:   result.RefreshToken,
    UserID:         result.UserID,
    Email:          result.Email,
    OnboardingStep: result.OnboardingStep,
})
```

Update `TokenResponse` struct:

```go
type TokenResponse struct {
    AccessToken       string `json:"access_token"        example:"eyJhbGci..."`
    RefreshToken      string `json:"refresh_token"       example:"eyJhbGci..."`
    UserID            string `json:"user_id"             example:"550e8400-e29b-41d4-a716-446655440000"`
    Email             string `json:"email"               example:"user@example.com"`
    NeedsVerification bool   `json:"needs_verification"  example:"true"`
    OnboardingStep    int    `json:"onboarding_step"     example:"0"`
}
```

- [ ] **Step 2: Add handler tests for new fields**

In `auth_handler_test.go`, add:

```go
func TestRegisterHandler_ReturnsNeedsVerification(t *testing.T) {
    repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
    r := setupAuthRouter(repo, &fakeAuthProvider{})

    body := jsonBody(t, map[string]string{"email": "new@example.com", "password": "secret123"})
    req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/register", body)
    req.Header.Set("Content-Type", "application/json")
    w := httptest.NewRecorder()
    r.ServeHTTP(w, req)

    if w.Code != 201 {
        t.Fatalf("expected 201, got %d: %s", w.Code, w.Body.String())
    }
    var resp map[string]any
    json.Unmarshal(w.Body.Bytes(), &resp)
    if resp["needs_verification"] != true {
        t.Errorf("expected needs_verification=true, got %v", resp["needs_verification"])
    }
}

func TestLoginHandler_ReturnsOnboardingStep(t *testing.T) {
    repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
    r := setupAuthRouter(repo, &fakeAuthProvider{})

    body := jsonBody(t, map[string]string{"email": "user@example.com", "password": "secret123"})
    req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login", body)
    req.Header.Set("Content-Type", "application/json")
    w := httptest.NewRecorder()
    r.ServeHTTP(w, req)

    if w.Code != 200 {
        t.Fatalf("expected 200, got %d: %s", w.Code, w.Body.String())
    }
    var resp map[string]any
    json.Unmarshal(w.Body.Bytes(), &resp)
    if _, ok := resp["onboarding_step"]; !ok {
        t.Error("expected onboarding_step in login response")
    }
}
```

- [ ] **Step 3: Run all tests**

```bash
export PATH="/opt/homebrew/bin:$PATH" && go test ./...
```

Expected: all pass.

- [ ] **Step 4: Regenerate swagger**

```bash
export PATH="$HOME/go/bin:/opt/homebrew/bin:$PATH" && swag init -g cmd/server/main.go -o docs
```

- [ ] **Step 5: Commit**

```bash
git add internal/delivery/http/auth_handler.go internal/delivery/http/auth_handler_test.go docs/
git commit -m "feat: auth responses include needs_verification and onboarding_step"
```

---

## Self-Review

**Spec coverage:**
- ✅ Register with email → `POST /auth/register` → `needs_verification: true`
- ✅ Login → `POST /auth/login` → `onboarding_step` in response so frontend knows where to resume
- ✅ Google OAuth → frontend gets JWT from Supabase SDK, hits same `/onboarding/*` flow
- ✅ Onboarding step 1: profile info → `POST /onboarding/profile`
- ✅ Onboarding step 2: placement (self-reported levels) → `POST /onboarding/placement`
- ✅ Onboarding step 3: goals → `POST /onboarding/goals`
- ✅ Onboarding step 4: summary → `GET /onboarding/summary` (marks complete)
- ✅ Resume support → `GET /onboarding/status` + `onboarding_step` in login response
- ⏳ Quick placement test (actual questions) → planned separately per discussion

**Placeholder scan:** No TBDs. All code blocks complete.

**Type consistency:** `OnboardingRepo` interface in `usecase/onboarding.go` matches method signatures in `postgres/onboarding.go` and fake in tests.
