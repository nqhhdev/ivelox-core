package usecase_test

import (
	"context"
	"errors"
	"fmt"
	"testing"
	"time"

	"github.com/nqhhdev/ivelox-core/internal/domain"
	"github.com/nqhhdev/ivelox-core/internal/health"
	"github.com/nqhhdev/ivelox-core/internal/usecase"
)

func ptr[T any](v T) *T { return &v }

type fakeFoodCacheRepo struct {
	items      map[string]*domain.FoodCache
	getCalls   int
	getNames   []string
	upserts    []domain.FoodItem
	upsertSrc  []string
	getErr     error
	upsertErr  error
}

func (f *fakeFoodCacheRepo) GetByNormalizedName(_ context.Context, name string) (*domain.FoodCache, error) {
	f.getCalls++
	f.getNames = append(f.getNames, name)
	if f.getErr != nil {
		return nil, f.getErr
	}
	if f.items == nil {
		return nil, nil
	}
	item, ok := f.items[name]
	if !ok {
		return nil, nil
	}
	return item, nil
}

func (f *fakeFoodCacheRepo) UpsertFromItem(_ context.Context, item domain.FoodItem, source string) (*domain.FoodCache, error) {
	if f.upsertErr != nil {
		return nil, f.upsertErr
	}
	f.upserts = append(f.upserts, item)
	f.upsertSrc = append(f.upsertSrc, source)
	return &domain.FoodCache{
		NormalizedName:     health.NormalizeFoodName(item.Name),
		DefaultServingQty:  item.Quantity,
		DefaultServingUnit: item.Unit,
		Kcal:               item.Kcal,
		ProteinG:           item.ProteinG,
		CarbG:              item.CarbG,
		FatG:               item.FatG,
		Source:             source,
		Confidence:         item.Confidence,
		UpdatedAt:          time.Now(),
	}, nil
}

type fakeNutritionResolver struct {
	textCalls   int
	imageCalls  int
	lastText    string
	lastHint    string
	lastQty     *float64
	lastUnit    *domain.FoodUnit
	textResult  *domain.ResolveResult
	imageResult *domain.ResolveResult
	textErr     error
	imageErr    error
}

func (f *fakeNutritionResolver) ResolveText(_ context.Context, text string, quantity *float64, unit *domain.FoodUnit) (*domain.ResolveResult, error) {
	f.textCalls++
	f.lastText = text
	f.lastQty = quantity
	f.lastUnit = unit
	if f.textErr != nil {
		return nil, f.textErr
	}
	return f.textResult, nil
}

func (f *fakeNutritionResolver) ResolveImage(_ context.Context, _ []byte, _ string, hint string) (*domain.ResolveResult, error) {
	f.imageCalls++
	f.lastHint = hint
	if f.imageErr != nil {
		return nil, f.imageErr
	}
	return f.imageResult, nil
}

func TestFoodResolve_CacheHit_SkipsAI(t *testing.T) {
	cache := &fakeFoodCacheRepo{items: map[string]*domain.FoodCache{
		"com tam": {
			NormalizedName:     "com tam",
			DefaultServingQty:  1,
			DefaultServingUnit: domain.UnitServing,
			Kcal:               550,
			ProteinG:           30,
			CarbG:              70,
			FatG:               15,
			Confidence:         0.8,
			UpdatedAt:          time.Now(),
		},
	}}
	resolver := &fakeNutritionResolver{}
	uc := usecase.NewFoodResolveUsecase(cache, resolver)

	got, err := uc.Resolve(context.Background(), usecase.FoodResolveInput{
		Text:     "Cơm Tấm",
		Quantity: ptr(2.0),
		Unit:     ptr(domain.UnitServing),
	})
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if got.Source != "cache" {
		t.Errorf("source = %q, want cache", got.Source)
	}
	if resolver.textCalls != 0 || resolver.imageCalls != 0 {
		t.Fatalf("AI must not be called on cache hit (text=%d image=%d)", resolver.textCalls, resolver.imageCalls)
	}
	if len(got.Items) != 1 {
		t.Fatalf("items = %d, want 1", len(got.Items))
	}
	item := got.Items[0]
	if item.Kcal != 1100 || item.ProteinG != 60 || item.CarbG != 140 || item.FatG != 30 {
		t.Errorf("scaled macros = %+v, want 2x of 550/30/70/15", item)
	}
	if item.Quantity != 2 || item.Unit != domain.UnitServing {
		t.Errorf("qty/unit = %v %q, want 2 serving", item.Quantity, item.Unit)
	}
}

func TestFoodResolve_CacheMiss_CallsAIAndUpserts(t *testing.T) {
	cache := &fakeFoodCacheRepo{}
	resolver := &fakeNutritionResolver{
		textResult: &domain.ResolveResult{
			Items: []domain.FoodItem{{
				Name: "banh mi", Quantity: 1, Unit: domain.UnitPiece,
				Kcal: 400, ProteinG: 15, CarbG: 50, FatG: 12, Confidence: 0.85,
			}},
			Source: "ai",
		},
	}
	uc := usecase.NewFoodResolveUsecase(cache, resolver)

	got, err := uc.Resolve(context.Background(), usecase.FoodResolveInput{Text: "Bánh Mì"})
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if got.Source != "ai" {
		t.Errorf("source = %q, want ai", got.Source)
	}
	if resolver.textCalls != 1 {
		t.Fatalf("ResolveText calls = %d, want 1", resolver.textCalls)
	}
	if resolver.imageCalls != 0 {
		t.Errorf("ResolveImage must not be called")
	}
	if len(cache.upserts) != 1 {
		t.Fatalf("upserts = %d, want 1", len(cache.upserts))
	}
	if cache.upsertSrc[0] != "ai" {
		t.Errorf("upsert source = %q, want ai", cache.upsertSrc[0])
	}
	if cache.upserts[0].Name != "banh mi" {
		t.Errorf("upserted name = %q, want banh mi", cache.upserts[0].Name)
	}
}

func TestFoodResolve_Image_SkipsCache_UpsertsEach(t *testing.T) {
	cache := &fakeFoodCacheRepo{items: map[string]*domain.FoodCache{
		"com tam": {NormalizedName: "com tam", Confidence: 0.99, UpdatedAt: time.Now()},
	}}
	resolver := &fakeNutritionResolver{
		imageResult: &domain.ResolveResult{
			Items: []domain.FoodItem{
				{Name: "rice", Quantity: 200, Unit: domain.UnitG, Kcal: 260, Confidence: 0.7},
				{Name: "pork", Quantity: 80, Unit: domain.UnitG, Kcal: 180, Confidence: 0.75},
			},
			Source: "ai",
		},
	}
	uc := usecase.NewFoodResolveUsecase(cache, resolver)

	got, err := uc.Resolve(context.Background(), usecase.FoodResolveInput{
		Text:       "Cơm Tấm",
		ImageBytes: []byte{0x89, 0x50, 0x4e},
		ImageMIME:  "image/jpeg",
	})
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if got.Source != "ai" {
		t.Errorf("source = %q, want ai", got.Source)
	}
	if cache.getCalls != 0 {
		t.Errorf("cache lookup must be skipped when image present, getCalls=%d", cache.getCalls)
	}
	if resolver.imageCalls != 1 {
		t.Fatalf("ResolveImage calls = %d, want 1", resolver.imageCalls)
	}
	if resolver.textCalls != 0 {
		t.Errorf("ResolveText must not be called for image")
	}
	if len(cache.upserts) != 2 {
		t.Fatalf("upserts = %d, want 2", len(cache.upserts))
	}
	if cache.upsertSrc[0] != "ai" || cache.upsertSrc[1] != "ai" {
		t.Errorf("upsert sources = %v, want ai,ai", cache.upsertSrc)
	}
	if len(got.Items) != 2 {
		t.Errorf("items = %d, want 2", len(got.Items))
	}
}

func TestFoodResolve_EmptyTextNoImage_InvalidInput(t *testing.T) {
	uc := usecase.NewFoodResolveUsecase(&fakeFoodCacheRepo{}, &fakeNutritionResolver{})

	_, err := uc.Resolve(context.Background(), usecase.FoodResolveInput{})
	if err == nil {
		t.Fatal("expected error, got nil")
	}
	if !errors.Is(err, usecase.ErrInvalidInput) {
		t.Errorf("error = %v, want ErrInvalidInput", err)
	}
}

func TestFoodResolve_LowConfidence_FallsBackToAI(t *testing.T) {
	cache := &fakeFoodCacheRepo{items: map[string]*domain.FoodCache{
		"pho": {
			NormalizedName:     "pho",
			DefaultServingQty:  1,
			DefaultServingUnit: domain.UnitServing,
			Kcal:               400,
			Confidence:         0.59,
			UpdatedAt:          time.Now(),
		},
	}}
	resolver := &fakeNutritionResolver{
		textResult: &domain.ResolveResult{
			Items:  []domain.FoodItem{{Name: "pho", Quantity: 1, Unit: domain.UnitServing, Kcal: 450, Confidence: 0.9}},
			Source: "ai",
		},
	}
	uc := usecase.NewFoodResolveUsecase(cache, resolver)

	got, err := uc.Resolve(context.Background(), usecase.FoodResolveInput{Text: "Pho"})
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if got.Source != "ai" {
		t.Errorf("source = %q, want ai", got.Source)
	}
	if resolver.textCalls != 1 {
		t.Fatalf("ResolveText calls = %d, want 1", resolver.textCalls)
	}
}

func TestFoodResolve_StaleCache_FallsBackToAI(t *testing.T) {
	cache := &fakeFoodCacheRepo{items: map[string]*domain.FoodCache{
		"pho": {
			NormalizedName:     "pho",
			DefaultServingQty:  1,
			DefaultServingUnit: domain.UnitServing,
			Kcal:               400,
			Confidence:         0.95,
			UpdatedAt:          time.Now().Add(-90 * 24 * time.Hour),
		},
	}}
	resolver := &fakeNutritionResolver{
		textResult: &domain.ResolveResult{
			Items:  []domain.FoodItem{{Name: "pho", Quantity: 1, Unit: domain.UnitServing, Kcal: 420, Confidence: 0.9}},
			Source: "ai",
		},
	}
	uc := usecase.NewFoodResolveUsecase(cache, resolver)

	got, err := uc.Resolve(context.Background(), usecase.FoodResolveInput{Text: "Pho"})
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if got.Source != "ai" {
		t.Errorf("source = %q, want ai", got.Source)
	}
	if resolver.textCalls != 1 {
		t.Fatalf("ResolveText calls = %d, want 1", resolver.textCalls)
	}
}

func TestFoodResolve_UnitMismatch_FallsBackToAI(t *testing.T) {
	cache := &fakeFoodCacheRepo{items: map[string]*domain.FoodCache{
		"sua": {
			NormalizedName:     "sua",
			DefaultServingQty:  200,
			DefaultServingUnit: domain.UnitML,
			Kcal:               120,
			Confidence:         0.9,
			UpdatedAt:          time.Now(),
		},
	}}
	resolver := &fakeNutritionResolver{
		textResult: &domain.ResolveResult{
			Items:  []domain.FoodItem{{Name: "sua", Quantity: 1, Unit: domain.UnitServing, Kcal: 130, Confidence: 0.8}},
			Source: "ai",
		},
	}
	uc := usecase.NewFoodResolveUsecase(cache, resolver)

	got, err := uc.Resolve(context.Background(), usecase.FoodResolveInput{
		Text:     "Sữa",
		Quantity: ptr(1.0),
		Unit:     ptr(domain.UnitServing),
	})
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if got.Source != "ai" {
		t.Errorf("source = %q, want ai", got.Source)
	}
	if resolver.textCalls != 1 {
		t.Fatalf("ResolveText calls = %d, want 1", resolver.textCalls)
	}
}

func TestFoodResolve_CacheNotFoundError_TreatedAsMiss(t *testing.T) {
	cache := &fakeFoodCacheRepo{getErr: fmt.Errorf("food cache not found")}
	resolver := &fakeNutritionResolver{
		textResult: &domain.ResolveResult{
			Items:  []domain.FoodItem{{Name: "apple", Quantity: 1, Unit: domain.UnitPiece, Kcal: 80, Confidence: 0.8}},
			Source: "ai",
		},
	}
	uc := usecase.NewFoodResolveUsecase(cache, resolver)

	got, err := uc.Resolve(context.Background(), usecase.FoodResolveInput{Text: "apple"})
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if got.Source != "ai" {
		t.Errorf("source = %q, want ai", got.Source)
	}
	if resolver.textCalls != 1 {
		t.Fatalf("ResolveText calls = %d, want 1", resolver.textCalls)
	}
}
