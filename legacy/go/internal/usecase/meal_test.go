package usecase_test

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/nqhhdev/ivelox-core/internal/domain"
	"github.com/nqhhdev/ivelox-core/internal/health"
	"github.com/nqhhdev/ivelox-core/internal/usecase"
)

type fakeMealLogRepo struct {
	meals     map[uuid.UUID]domain.MealLog
	createErr error
	listErr   error
	deleteErr error
	sumErr    error
}

func newFakeMealLogRepo() *fakeMealLogRepo {
	return &fakeMealLogRepo{meals: map[uuid.UUID]domain.MealLog{}}
}

func (f *fakeMealLogRepo) Create(_ context.Context, m *domain.MealLog) error {
	if f.createErr != nil {
		return f.createErr
	}
	if m.ID == uuid.Nil {
		m.ID = uuid.New()
	}
	if m.LoggedAt.IsZero() {
		m.LoggedAt = time.Now().UTC()
	}
	cp := *m
	f.meals[m.ID] = cp
	return nil
}

func (f *fakeMealLogRepo) ListByUserDate(_ context.Context, userID uuid.UUID, day time.Time) ([]domain.MealLog, error) {
	if f.listErr != nil {
		return nil, f.listErr
	}
	start, end := health.CivilDayBounds(day)
	var out []domain.MealLog
	for _, m := range f.meals {
		if m.UserID != userID {
			continue
		}
		if m.LoggedAt.Before(start) || !m.LoggedAt.Before(end) {
			continue
		}
		out = append(out, m)
	}
	if out == nil {
		out = []domain.MealLog{}
	}
	return out, nil
}

func (f *fakeMealLogRepo) Delete(_ context.Context, userID, id uuid.UUID) error {
	if f.deleteErr != nil {
		return f.deleteErr
	}
	m, ok := f.meals[id]
	if !ok || m.UserID != userID {
		return fmt.Errorf("meal log not found")
	}
	delete(f.meals, id)
	return nil
}

func (f *fakeMealLogRepo) SummarizeDay(_ context.Context, userID uuid.UUID, day time.Time) (*domain.DayMealSummary, error) {
	if f.sumErr != nil {
		return nil, f.sumErr
	}
	start, end := health.CivilDayBounds(day)
	var s domain.DayMealSummary
	for _, m := range f.meals {
		if m.UserID != userID {
			continue
		}
		if m.LoggedAt.Before(start) || !m.LoggedAt.Before(end) {
			continue
		}
		s.EatenKcal += m.Kcal
		s.ProteinG += m.ProteinG
		s.CarbG += m.CarbG
		s.FatG += m.FatG
		s.MealCount++
	}
	return &s, nil
}

func validCreateInput(userID uuid.UUID) usecase.CreateMealInput {
	return usecase.CreateMealInput{
		UserID:   userID,
		RawInput: "pho bo",
		Quantity: 1,
		Unit:     domain.UnitServing,
		Kcal:     400,
		ProteinG: 20,
		CarbG:    50,
		FatG:     10,
	}
}

func TestMeal_Create_RejectsNonPositiveQuantity(t *testing.T) {
	repo := newFakeMealLogRepo()
	uc := usecase.NewMealUsecase(repo)
	in := validCreateInput(uuid.New())
	in.Quantity = 0

	_, err := uc.Create(context.Background(), in)
	if !errors.Is(err, usecase.ErrInvalidInput) {
		t.Fatalf("error = %v, want ErrInvalidInput", err)
	}
	if len(repo.meals) != 0 {
		t.Fatal("expected no meal created")
	}
}

func TestMeal_Create_RejectsNegativeKcal(t *testing.T) {
	repo := newFakeMealLogRepo()
	uc := usecase.NewMealUsecase(repo)
	in := validCreateInput(uuid.New())
	in.Kcal = -1

	_, err := uc.Create(context.Background(), in)
	if !errors.Is(err, usecase.ErrInvalidInput) {
		t.Fatalf("error = %v, want ErrInvalidInput", err)
	}
}

func TestMeal_Create_RejectsNegativeMacros(t *testing.T) {
	uc := usecase.NewMealUsecase(newFakeMealLogRepo())
	cases := []struct {
		name string
		mut  func(*usecase.CreateMealInput)
	}{
		{"protein", func(in *usecase.CreateMealInput) { in.ProteinG = -1 }},
		{"carb", func(in *usecase.CreateMealInput) { in.CarbG = -0.1 }},
		{"fat", func(in *usecase.CreateMealInput) { in.FatG = -5 }},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			in := validCreateInput(uuid.New())
			tc.mut(&in)
			_, err := uc.Create(context.Background(), in)
			if !errors.Is(err, usecase.ErrInvalidInput) {
				t.Fatalf("error = %v, want ErrInvalidInput", err)
			}
		})
	}
}

func TestMeal_Create_RejectsInvalidMealType(t *testing.T) {
	uc := usecase.NewMealUsecase(newFakeMealLogRepo())
	in := validCreateInput(uuid.New())
	bad := "brunch"
	in.MealType = &bad

	_, err := uc.Create(context.Background(), in)
	if !errors.Is(err, usecase.ErrInvalidInput) {
		t.Fatalf("error = %v, want ErrInvalidInput", err)
	}
}

func TestMeal_Create_DefaultsLoggedAt(t *testing.T) {
	repo := newFakeMealLogRepo()
	uc := usecase.NewMealUsecase(repo)
	before := time.Now().UTC().Add(-time.Second)

	got, err := uc.Create(context.Background(), validCreateInput(uuid.New()))
	if err != nil {
		t.Fatalf("Create: %v", err)
	}
	if got.ID == uuid.Nil {
		t.Fatal("expected assigned id")
	}
	if got.LoggedAt.Before(before) || got.LoggedAt.After(time.Now().UTC().Add(time.Second)) {
		t.Fatalf("LoggedAt = %v, want ~now", got.LoggedAt)
	}
}

func TestMeal_Delete_WrongUserFails(t *testing.T) {
	repo := newFakeMealLogRepo()
	uc := usecase.NewMealUsecase(repo)
	owner := uuid.New()

	created, err := uc.Create(context.Background(), validCreateInput(owner))
	if err != nil {
		t.Fatalf("Create: %v", err)
	}

	err = uc.Delete(context.Background(), uuid.New(), created.ID)
	if err == nil {
		t.Fatal("expected delete by non-owner to fail")
	}
	if !strings.Contains(strings.ToLower(err.Error()), "not found") {
		t.Fatalf("error = %v, want not found", err)
	}
	if _, ok := repo.meals[created.ID]; !ok {
		t.Fatal("meal should still exist after failed delete")
	}
}

func TestMeal_TodaySummary_Aggregates(t *testing.T) {
	repo := newFakeMealLogRepo()
	uc := usecase.NewMealUsecase(repo)
	userID := uuid.New()
	day := time.Date(2026, 8, 23, 12, 0, 0, 0, time.UTC)
	logged := day.Add(-2 * time.Hour)

	in1 := validCreateInput(userID)
	in1.Kcal, in1.ProteinG, in1.CarbG, in1.FatG = 400, 20, 50, 10
	in1.LoggedAt = &logged

	in2 := validCreateInput(userID)
	in2.RawInput = "com tam"
	in2.Kcal, in2.ProteinG, in2.CarbG, in2.FatG = 100, 5, 10, 2
	in2.LoggedAt = &logged

	if _, err := uc.Create(context.Background(), in1); err != nil {
		t.Fatalf("Create 1: %v", err)
	}
	if _, err := uc.Create(context.Background(), in2); err != nil {
		t.Fatalf("Create 2: %v", err)
	}

	sum, err := uc.TodaySummary(context.Background(), userID, day)
	if err != nil {
		t.Fatalf("TodaySummary: %v", err)
	}
	if sum.EatenKcal != 500 || sum.ProteinG != 25 || sum.CarbG != 60 || sum.FatG != 12 || sum.MealCount != 2 {
		t.Fatalf("unexpected summary: %+v", sum)
	}
}

func TestMeal_List_ByDay(t *testing.T) {
	repo := newFakeMealLogRepo()
	uc := usecase.NewMealUsecase(repo)
	userID := uuid.New()
	day := time.Date(2026, 8, 23, 12, 0, 0, 0, time.UTC)
	inDay := day.Add(-time.Hour)
	outDay := day.AddDate(0, 0, -1)

	a := validCreateInput(userID)
	a.LoggedAt = &inDay
	b := validCreateInput(userID)
	b.RawInput = "yesterday"
	b.LoggedAt = &outDay

	if _, err := uc.Create(context.Background(), a); err != nil {
		t.Fatalf("Create a: %v", err)
	}
	if _, err := uc.Create(context.Background(), b); err != nil {
		t.Fatalf("Create b: %v", err)
	}

	listed, err := uc.List(context.Background(), userID, day)
	if err != nil {
		t.Fatalf("List: %v", err)
	}
	if len(listed) != 1 {
		t.Fatalf("List len = %d, want 1", len(listed))
	}
	if listed[0].RawInput != "pho bo" {
		t.Fatalf("RawInput = %q, want pho bo", listed[0].RawInput)
	}
}

func TestMeal_List_IncludesICTMorningOnCivilDate(t *testing.T) {
	repo := newFakeMealLogRepo()
	uc := usecase.NewMealUsecase(repo)
	userID := uuid.New()

	logged := time.Date(2026, 8, 23, 1, 0, 0, 0, health.Location())
	in := validCreateInput(userID)
	in.LoggedAt = &logged
	if _, err := uc.Create(context.Background(), in); err != nil {
		t.Fatalf("Create: %v", err)
	}

	day, err := health.ParseCivilDate("2026-08-23")
	if err != nil {
		t.Fatal(err)
	}
	listed, err := uc.List(context.Background(), userID, day)
	if err != nil {
		t.Fatalf("List: %v", err)
	}
	if len(listed) != 1 {
		t.Fatalf("List len = %d, want 1 (ICT 01:00 belongs to civil 2026-08-23)", len(listed))
	}

	sum, err := uc.TodaySummary(context.Background(), userID, day)
	if err != nil {
		t.Fatalf("TodaySummary: %v", err)
	}
	if sum.MealCount != 1 {
		t.Fatalf("TodaySummary MealCount = %d, want 1", sum.MealCount)
	}
}
