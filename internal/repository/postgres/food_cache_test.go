package postgres_test

import (
	"context"
	"os"
	"testing"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/nqhhdev/ivelox-core/internal/domain"
	"github.com/nqhhdev/ivelox-core/internal/health"
	"github.com/nqhhdev/ivelox-core/internal/repository/postgres"
)

func testPool(t *testing.T) *pgxpool.Pool {
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
			where table_schema = 'public' and table_name = 'food_cache'
		)`,
	).Scan(&exists)
	if err != nil {
		t.Fatalf("check food_cache table: %v", err)
	}
	if !exists {
		t.Skip("public.food_cache table missing — skipping integration test")
	}
	return pool
}

func TestFoodCacheRepository_UpsertAndGet(t *testing.T) {
	pool := testPool(t)
	repo := postgres.NewFoodCacheRepository(pool)
	ctx := context.Background()

	item := domain.FoodItem{
		Name: "Pho Bo " + uuid.NewString()[:8], Quantity: 1, Unit: domain.UnitServing,
		Kcal: 450, ProteinG: 25, CarbG: 55, FatG: 12, Confidence: 0.9,
	}
	saved, err := repo.UpsertFromItem(ctx, item, "ai")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		_, _ = pool.Exec(ctx, `delete from public.food_cache where id = $1`, saved.ID)
	})

	got, err := repo.GetByNormalizedName(ctx, health.NormalizeFoodName(item.Name))
	if err != nil {
		t.Fatal(err)
	}
	if got.ID != saved.ID || got.Kcal != 450 {
		t.Fatalf("unexpected cache row: %+v", got)
	}
	if got.NormalizedName != health.NormalizeFoodName(item.Name) {
		t.Fatalf("normalized name = %q, want %q", got.NormalizedName, health.NormalizeFoodName(item.Name))
	}
	if got.Source != "ai" || got.DefaultServingUnit != domain.UnitServing {
		t.Fatalf("unexpected source/unit: %+v", got)
	}
}
