package postgres

import (
	"context"
	"errors"
	"fmt"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/nqhhdev/ivelox-core/internal/domain"
	"github.com/nqhhdev/ivelox-core/internal/health"
)

type FoodCacheRepository struct {
	db *pgxpool.Pool
}

func NewFoodCacheRepository(db *pgxpool.Pool) *FoodCacheRepository {
	return &FoodCacheRepository{db: db}
}

var _ domain.FoodCacheRepository = (*FoodCacheRepository)(nil)

const foodCacheColumns = `id, normalized_name, aliases, default_serving_qty, default_serving_unit,
	kcal, protein_g, carb_g, fat_g, source, confidence, updated_at`

func (r *FoodCacheRepository) GetByNormalizedName(ctx context.Context, name string) (*domain.FoodCache, error) {
	var fc domain.FoodCache
	var unit string
	err := r.db.QueryRow(ctx,
		`select `+foodCacheColumns+`
		 from public.food_cache where normalized_name = $1`,
		name,
	).Scan(
		&fc.ID, &fc.NormalizedName, &fc.Aliases, &fc.DefaultServingQty, &unit,
		&fc.Kcal, &fc.ProteinG, &fc.CarbG, &fc.FatG, &fc.Source, &fc.Confidence, &fc.UpdatedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, fmt.Errorf("food cache not found: %w", err)
	}
	if err != nil {
		return nil, fmt.Errorf("query food cache by normalized name: %w", err)
	}
	fc.DefaultServingUnit = domain.FoodUnit(unit)
	return &fc, nil
}

func (r *FoodCacheRepository) UpsertFromItem(ctx context.Context, item domain.FoodItem, source string) (*domain.FoodCache, error) {
	normalized := health.NormalizeFoodName(item.Name)
	var fc domain.FoodCache
	var unit string
	err := r.db.QueryRow(ctx,
		`insert into public.food_cache (
			normalized_name, default_serving_qty, default_serving_unit,
			kcal, protein_g, carb_g, fat_g, source, confidence, updated_at
		) values ($1, $2, $3, $4, $5, $6, $7, $8, $9, now())
		on conflict (normalized_name) do update set
			default_serving_qty  = excluded.default_serving_qty,
			default_serving_unit = excluded.default_serving_unit,
			kcal                 = excluded.kcal,
			protein_g            = excluded.protein_g,
			carb_g               = excluded.carb_g,
			fat_g                = excluded.fat_g,
			source               = excluded.source,
			confidence           = excluded.confidence,
			updated_at           = now()
		returning `+foodCacheColumns,
		normalized, item.Quantity, string(item.Unit),
		item.Kcal, item.ProteinG, item.CarbG, item.FatG, source, item.Confidence,
	).Scan(
		&fc.ID, &fc.NormalizedName, &fc.Aliases, &fc.DefaultServingQty, &unit,
		&fc.Kcal, &fc.ProteinG, &fc.CarbG, &fc.FatG, &fc.Source, &fc.Confidence, &fc.UpdatedAt,
	)
	if err != nil {
		return nil, fmt.Errorf("upsert food cache: %w", err)
	}
	fc.DefaultServingUnit = domain.FoodUnit(unit)
	return &fc, nil
}
