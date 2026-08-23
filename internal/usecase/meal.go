package usecase

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/nqhhdev/ivelox-core/internal/domain"
)

type CreateMealInput struct {
	UserID      uuid.UUID
	RawInput    string
	FoodCacheID *uuid.UUID
	Quantity    float64
	Unit        domain.FoodUnit
	Kcal        float64
	ProteinG    float64
	CarbG       float64
	FatG        float64
	MealType    *string
	LoggedAt    *time.Time // default now
}

type MealUsecase struct {
	meals domain.MealLogRepository
}

func NewMealUsecase(meals domain.MealLogRepository) *MealUsecase {
	return &MealUsecase{meals: meals}
}

func validMealType(s string) bool {
	switch s {
	case "breakfast", "lunch", "dinner", "snack":
		return true
	default:
		return false
	}
}

func (uc *MealUsecase) Create(ctx context.Context, in CreateMealInput) (*domain.MealLog, error) {
	if in.Quantity <= 0 {
		return nil, fmt.Errorf("%w: quantity must be > 0", ErrInvalidInput)
	}
	if in.Kcal < 0 || in.ProteinG < 0 || in.CarbG < 0 || in.FatG < 0 {
		return nil, fmt.Errorf("%w: macros must be >= 0", ErrInvalidInput)
	}

	var mealType *string
	if in.MealType != nil {
		mt := strings.TrimSpace(*in.MealType)
		if mt != "" {
			if !validMealType(mt) {
				return nil, fmt.Errorf("%w: invalid meal_type", ErrInvalidInput)
			}
			mealType = &mt
		}
	}

	loggedAt := time.Now().UTC()
	if in.LoggedAt != nil {
		loggedAt = in.LoggedAt.UTC()
	}

	m := &domain.MealLog{
		UserID:      in.UserID,
		FoodCacheID: in.FoodCacheID,
		RawInput:    in.RawInput,
		Quantity:    in.Quantity,
		Unit:        in.Unit,
		Kcal:        in.Kcal,
		ProteinG:    in.ProteinG,
		CarbG:       in.CarbG,
		FatG:        in.FatG,
		MealType:    mealType,
		LoggedAt:    loggedAt,
	}
	if err := uc.meals.Create(ctx, m); err != nil {
		return nil, err
	}
	return m, nil
}

func (uc *MealUsecase) List(ctx context.Context, userID uuid.UUID, day time.Time) ([]domain.MealLog, error) {
	return uc.meals.ListByUserDate(ctx, userID, day)
}

func (uc *MealUsecase) Delete(ctx context.Context, userID, id uuid.UUID) error {
	return uc.meals.Delete(ctx, userID, id)
}

func (uc *MealUsecase) TodaySummary(ctx context.Context, userID uuid.UUID, day time.Time) (*domain.DayMealSummary, error) {
	return uc.meals.SummarizeDay(ctx, userID, day)
}
