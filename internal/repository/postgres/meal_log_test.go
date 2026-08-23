package postgres_test

import (
	"context"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/nqhhdev/ivelox-core/internal/domain"
	"github.com/nqhhdev/ivelox-core/internal/repository/postgres"
)

func mealLogTestPool(t *testing.T) *pgxpool.Pool {
	t.Helper()
	dbURL := os.Getenv("DATABASE_URL")
	if dbURL == "" {
		t.Skip("DATABASE_URL not set — skipping integration test")
	}
	pool, err := pgxpool.New(context.Background(), dbURL)
	if err != nil {
		t.Fatalf("failed to connect to DB: %v", err)
	}
	t.Cleanup(pool.Close)

	var exists bool
	err = pool.QueryRow(context.Background(),
		`select exists (
			select 1 from information_schema.tables
			where table_schema = 'public' and table_name = 'meal_logs'
		)`,
	).Scan(&exists)
	if err != nil {
		t.Skipf("meal_logs table check failed (DB unreachable or missing): %v", err)
	}
	if !exists {
		t.Skip("public.meal_logs table missing — skipping integration test")
	}
	return pool
}

func mealLogTestUserID(t *testing.T, pool *pgxpool.Pool) uuid.UUID {
	t.Helper()
	ctx := context.Background()
	var id uuid.UUID
	err := pool.QueryRow(ctx, `select id from auth.users limit 1`).Scan(&id)
	if err != nil {
		err = pool.QueryRow(ctx, `select id from public.profiles limit 1`).Scan(&id)
	}
	if err != nil {
		t.Skip("no auth user available for meal_logs FK — skipping integration test")
	}
	return id
}

func TestMealLogRepository_CreateListSummarizeDelete(t *testing.T) {
	pool := mealLogTestPool(t)
	repo := postgres.NewMealLogRepository(pool)
	ctx := context.Background()
	userID := mealLogTestUserID(t, pool)

	// Far-future UTC day so summary/list assertions are isolated from real data.
	start := time.Date(2099, 1, 15, 0, 0, 0, 0, time.UTC)
	end := start.AddDate(0, 0, 1)
	day := start.Add(12 * time.Hour)

	breakfast := "breakfast"
	inDay := &domain.MealLog{
		UserID: userID, RawInput: "pho " + uuid.NewString()[:8],
		Quantity: 1, Unit: domain.UnitServing,
		Kcal: 400, ProteinG: 20, CarbG: 50, FatG: 10,
		MealType: &breakfast, LoggedAt: start.Add(8 * time.Hour),
	}
	edge := &domain.MealLog{
		UserID: userID, RawInput: "edge " + uuid.NewString()[:8],
		Quantity: 1, Unit: domain.UnitServing,
		Kcal: 100, ProteinG: 5, CarbG: 10, FatG: 2,
		LoggedAt: end.Add(-time.Second),
	}
	before := &domain.MealLog{
		UserID: userID, RawInput: "before " + uuid.NewString()[:8],
		Quantity: 1, Unit: domain.UnitServing,
		Kcal: 999, ProteinG: 1, CarbG: 1, FatG: 1,
		LoggedAt: start.Add(-time.Second),
	}
	after := &domain.MealLog{
		UserID: userID, RawInput: "after " + uuid.NewString()[:8],
		Quantity: 1, Unit: domain.UnitServing,
		Kcal: 888, ProteinG: 1, CarbG: 1, FatG: 1,
		LoggedAt: end,
	}

	for _, m := range []*domain.MealLog{inDay, edge, before, after} {
		if err := repo.Create(ctx, m); err != nil {
			if isMealLogFKError(err) {
				t.Skipf("meal_logs user FK rejected: %v", err)
			}
			t.Fatal(err)
		}
		id := m.ID
		t.Cleanup(func() {
			_, _ = pool.Exec(ctx, `delete from public.meal_logs where id = $1`, id)
		})
	}

	listed, err := repo.ListByUserDate(ctx, userID, day)
	if err != nil {
		t.Fatal(err)
	}
	if len(listed) != 2 {
		t.Fatalf("ListByUserDate len = %d, want 2", len(listed))
	}
	ids := map[uuid.UUID]bool{}
	for _, m := range listed {
		ids[m.ID] = true
		if m.LoggedAt.Before(start) || !m.LoggedAt.Before(end) {
			t.Fatalf("listed meal outside UTC day bounds: %v", m.LoggedAt)
		}
	}
	if !ids[inDay.ID] || !ids[edge.ID] {
		t.Fatalf("missing in-day meals; got ids=%v", ids)
	}
	if ids[before.ID] || ids[after.ID] {
		t.Fatalf("day-bound leak: before/after included; ids=%v", ids)
	}

	sum, err := repo.SummarizeDay(ctx, userID, day)
	if err != nil {
		t.Fatal(err)
	}
	if sum.EatenKcal != 500 || sum.ProteinG != 25 || sum.CarbG != 60 || sum.FatG != 12 || sum.MealCount != 2 {
		t.Fatalf("unexpected summary: %+v", sum)
	}

	wrongUser := uuid.New()
	if err := repo.Delete(ctx, wrongUser, inDay.ID); err == nil {
		t.Fatal("Delete by non-owner should fail")
	} else if !strings.Contains(err.Error(), "not found") {
		t.Fatalf("Delete non-owner error = %v, want not found", err)
	}

	still, err := repo.ListByUserDate(ctx, userID, day)
	if err != nil {
		t.Fatal(err)
	}
	stillHas := false
	for _, m := range still {
		if m.ID == inDay.ID {
			stillHas = true
			break
		}
	}
	if !stillHas {
		t.Fatal("non-owner Delete removed meal")
	}

	if err := repo.Delete(ctx, userID, inDay.ID); err != nil {
		t.Fatal(err)
	}
	if err := repo.Delete(ctx, userID, inDay.ID); err == nil {
		t.Fatal("second Delete should return not found")
	} else if !strings.Contains(err.Error(), "not found") {
		t.Fatalf("second Delete error = %v, want not found", err)
	}

	sumAfter, err := repo.SummarizeDay(ctx, userID, day)
	if err != nil {
		t.Fatal(err)
	}
	if sumAfter.EatenKcal != 100 || sumAfter.MealCount != 1 {
		t.Fatalf("summary after delete: %+v", sumAfter)
	}
}

func isMealLogFKError(err error) bool {
	msg := err.Error()
	return strings.Contains(msg, "foreign key") || strings.Contains(msg, "violates foreign key")
}
