package usecase

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/nqhhdev/ivelox-core/internal/domain"
	"github.com/nqhhdev/ivelox-core/internal/health"
)

const (
	cacheMinConfidence = 0.6
	cacheTTL           = 90 * 24 * time.Hour
	resolveSourceCache = "cache"
	resolveSourceAI    = "ai"
)

// ErrInvalidInput is a 400-class error for empty text with no image.
var ErrInvalidInput = errors.New("invalid input")

type FoodResolveInput struct {
	Text       string
	Quantity   *float64
	Unit       *domain.FoodUnit
	ImageBytes []byte // optional
	ImageMIME  string
}

type FoodResolveUsecase struct {
	cache    domain.FoodCacheRepository
	resolver domain.NutritionResolver
}

func NewFoodResolveUsecase(cache domain.FoodCacheRepository, resolver domain.NutritionResolver) *FoodResolveUsecase {
	return &FoodResolveUsecase{cache: cache, resolver: resolver}
}

func (uc *FoodResolveUsecase) Resolve(ctx context.Context, in FoodResolveInput) (*domain.ResolveResult, error) {
	if len(in.ImageBytes) > 0 {
		return uc.resolveImage(ctx, in)
	}

	normalized := health.NormalizeFoodName(in.Text)
	if normalized == "" {
		return nil, fmt.Errorf("%w: text or image is required", ErrInvalidInput)
	}

	cached, err := uc.cache.GetByNormalizedName(ctx, normalized)
	if err != nil {
		if !isNotFound(err) {
			return nil, fmt.Errorf("get food cache: %w", err)
		}
		cached = nil
	}

	if useCache(cached, in.Quantity, in.Unit) {
		return resultFromCache(cached, in), nil
	}

	return uc.resolveText(ctx, in)
}

func (uc *FoodResolveUsecase) resolveImage(ctx context.Context, in FoodResolveInput) (*domain.ResolveResult, error) {
	result, err := uc.resolver.ResolveImage(ctx, in.ImageBytes, in.ImageMIME, in.Text)
	if err != nil {
		return nil, err
	}
	if err := uc.upsertItems(ctx, result.Items); err != nil {
		return nil, err
	}
	result.Source = resolveSourceAI
	return result, nil
}

func (uc *FoodResolveUsecase) resolveText(ctx context.Context, in FoodResolveInput) (*domain.ResolveResult, error) {
	result, err := uc.resolver.ResolveText(ctx, in.Text, in.Quantity, in.Unit)
	if err != nil {
		return nil, err
	}
	if err := uc.upsertItems(ctx, result.Items); err != nil {
		return nil, err
	}
	result.Source = resolveSourceAI
	return result, nil
}

func (uc *FoodResolveUsecase) upsertItems(ctx context.Context, items []domain.FoodItem) error {
	for _, item := range items {
		if _, err := uc.cache.UpsertFromItem(ctx, item, resolveSourceAI); err != nil {
			return fmt.Errorf("upsert food cache: %w", err)
		}
	}
	return nil
}

func useCache(cached *domain.FoodCache, qty *float64, unit *domain.FoodUnit) bool {
	if cached == nil {
		return false
	}
	if cached.Confidence < cacheMinConfidence {
		return false
	}
	if time.Since(cached.UpdatedAt) >= cacheTTL {
		return false
	}
	if unit != nil && *unit != cached.DefaultServingUnit {
		return false
	}
	if qty != nil && cached.DefaultServingQty == 0 {
		return false
	}
	return true
}

func resultFromCache(cached *domain.FoodCache, in FoodResolveInput) *domain.ResolveResult {
	factor := 1.0
	if in.Quantity != nil && cached.DefaultServingQty != 0 {
		factor = *in.Quantity / cached.DefaultServingQty
	}

	qty := cached.DefaultServingQty
	if in.Quantity != nil {
		qty = *in.Quantity
	}
	unit := cached.DefaultServingUnit
	if in.Unit != nil {
		unit = *in.Unit
	}

	name := strings.TrimSpace(in.Text)
	if name == "" {
		name = cached.NormalizedName
	}

	return &domain.ResolveResult{
		Items: []domain.FoodItem{{
			Name:       name,
			Quantity:   qty,
			Unit:       unit,
			Kcal:       cached.Kcal * factor,
			ProteinG:   cached.ProteinG * factor,
			CarbG:      cached.CarbG * factor,
			FatG:       cached.FatG * factor,
			Confidence: cached.Confidence,
		}},
		Source: resolveSourceCache,
	}
}

func isNotFound(err error) bool {
	return err != nil && strings.Contains(strings.ToLower(err.Error()), "not found")
}
