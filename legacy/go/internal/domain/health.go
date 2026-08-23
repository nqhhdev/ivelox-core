package domain

import (
	"context"
	"time"

	"github.com/google/uuid"
)

type FoodUnit string

const (
	UnitG       FoodUnit = "g"
	UnitML      FoodUnit = "ml"
	UnitServing FoodUnit = "serving"
	UnitPiece   FoodUnit = "piece"
)

type FoodCache struct {
	ID                 uuid.UUID
	NormalizedName     string
	Aliases            []string
	DefaultServingQty  float64
	DefaultServingUnit FoodUnit
	Kcal               float64
	ProteinG           float64
	CarbG              float64
	FatG               float64
	Source             string // ai | manual
	Confidence         float64
	UpdatedAt          time.Time
}

type FoodItem struct {
	Name       string   `json:"name"`
	Quantity   float64  `json:"quantity"`
	Unit       FoodUnit `json:"unit"`
	Kcal       float64  `json:"kcal"`
	ProteinG   float64  `json:"protein_g"`
	CarbG      float64  `json:"carb_g"`
	FatG       float64  `json:"fat_g"`
	Confidence float64  `json:"confidence"`
}

type ResolveResult struct {
	Items  []FoodItem `json:"items"`
	Source string     `json:"source"` // cache | ai
	Notes  string     `json:"notes,omitempty"`
}

type MealLog struct {
	ID          uuid.UUID
	UserID      uuid.UUID
	FoodCacheID *uuid.UUID
	RawInput    string
	ImageURL    *string
	Quantity    float64
	Unit        FoodUnit
	Kcal        float64
	ProteinG    float64
	CarbG       float64
	FatG        float64
	MealType    *string
	LoggedAt    time.Time
}

type DayMealSummary struct {
	EatenKcal float64 `json:"eaten_kcal"`
	ProteinG  float64 `json:"protein_g"`
	CarbG     float64 `json:"carb_g"`
	FatG      float64 `json:"fat_g"`
	MealCount int     `json:"meal_count"`
}

type FoodCacheRepository interface {
	GetByNormalizedName(ctx context.Context, name string) (*FoodCache, error)
	UpsertFromItem(ctx context.Context, item FoodItem, source string) (*FoodCache, error)
}

type MealLogRepository interface {
	Create(ctx context.Context, m *MealLog) error
	ListByUserDate(ctx context.Context, userID uuid.UUID, day time.Time) ([]MealLog, error)
	Delete(ctx context.Context, userID, id uuid.UUID) error
	SummarizeDay(ctx context.Context, userID uuid.UUID, day time.Time) (*DayMealSummary, error)
}

type NutritionResolver interface {
	ResolveText(ctx context.Context, text string, quantity *float64, unit *FoodUnit) (*ResolveResult, error)
	ResolveImage(ctx context.Context, imageBytes []byte, mime string, hint string) (*ResolveResult, error)
}
