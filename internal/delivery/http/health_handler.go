package http

import (
	"context"
	"encoding/base64"
	"errors"
	"net/http"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/nqhhdev/ivelox-core/internal/domain"
	"github.com/nqhhdev/ivelox-core/internal/usecase"
)

type foodResolver interface {
	Resolve(ctx context.Context, in usecase.FoodResolveInput) (*domain.ResolveResult, error)
}

type mealService interface {
	Create(ctx context.Context, in usecase.CreateMealInput) (*domain.MealLog, error)
	List(ctx context.Context, userID uuid.UUID, day time.Time) ([]domain.MealLog, error)
	Delete(ctx context.Context, userID, id uuid.UUID) error
	TodaySummary(ctx context.Context, userID uuid.UUID, day time.Time) (*domain.DayMealSummary, error)
}

type HealthHandler struct {
	foodUC foodResolver
	mealUC mealService
}

func NewHealthHandler(foodUC foodResolver, mealUC mealService) *HealthHandler {
	return &HealthHandler{foodUC: foodUC, mealUC: mealUC}
}

func healthError(c *gin.Context, err error) {
	msg := err.Error()
	switch {
	case errors.Is(err, usecase.ErrInvalidInput):
		c.JSON(http.StatusBadRequest, gin.H{"error": msg})
	case strings.Contains(strings.ToLower(msg), "not found"):
		c.JSON(http.StatusNotFound, gin.H{"error": msg})
	default:
		c.JSON(http.StatusInternalServerError, gin.H{"error": msg})
	}
}

func parseUserID(c *gin.Context) (uuid.UUID, bool) {
	userID, err := uuid.Parse(c.GetString("userID"))
	if err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "invalid user"})
		return uuid.Nil, false
	}
	return userID, true
}

func parseDateQuery(c *gin.Context) (time.Time, bool) {
	raw := strings.TrimSpace(c.Query("date"))
	if raw == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "date is required (YYYY-MM-DD)"})
		return time.Time{}, false
	}
	day, err := time.ParseInLocation("2006-01-02", raw, time.UTC)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "date must be YYYY-MM-DD"})
		return time.Time{}, false
	}
	return day, true
}

func parseFoodUnit(s string) (domain.FoodUnit, bool) {
	u := domain.FoodUnit(s)
	switch u {
	case domain.UnitG, domain.UnitML, domain.UnitServing, domain.UnitPiece:
		return u, true
	default:
		return "", false
	}
}

func decodeImageBase64(s string) ([]byte, error) {
	s = strings.TrimSpace(s)
	if s == "" {
		return nil, nil
	}
	b, err := base64.StdEncoding.DecodeString(s)
	if err != nil {
		return nil, err
	}
	return b, nil
}

func mealLogResponse(m *domain.MealLog) MealLogResponse {
	return MealLogResponse{
		ID:       m.ID.String(),
		RawInput: m.RawInput,
		Quantity: m.Quantity,
		Unit:     string(m.Unit),
		Kcal:     m.Kcal,
		ProteinG: m.ProteinG,
		CarbG:    m.CarbG,
		FatG:     m.FatG,
		MealType: m.MealType,
		LoggedAt: m.LoggedAt.UTC().Format(time.RFC3339),
	}
}

// ResolveFood godoc
//
//	@Summary		Resolve food nutrition
//	@Description	Cache-first then AI estimate for text and/or image
//	@Tags			health
//	@Accept			json
//	@Produce		json
//	@Security		BearerAuth
//	@Param			body	body		ResolveFoodRequest	true	"Resolve payload"
//	@Success		200		{object}	domain.ResolveResult
//	@Failure		400		{object}	ErrorResponse
//	@Failure		401		{object}	ErrorResponse
//	@Failure		500		{object}	ErrorResponse
//	@Router			/health/foods/resolve [post]
func (h *HealthHandler) ResolveFood(c *gin.Context) {
	if _, ok := parseUserID(c); !ok {
		return
	}

	var req ResolveFoodRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	in := usecase.FoodResolveInput{
		Text: req.Text,
	}
	if req.Quantity != nil {
		in.Quantity = req.Quantity
	}
	if req.Unit != nil && strings.TrimSpace(*req.Unit) != "" {
		unit, ok := parseFoodUnit(*req.Unit)
		if !ok {
			c.JSON(http.StatusBadRequest, gin.H{"error": "invalid unit"})
			return
		}
		in.Unit = &unit
	}
	if req.ImageBase64 != nil {
		img, err := decodeImageBase64(*req.ImageBase64)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "invalid image_base64"})
			return
		}
		in.ImageBytes = img
	}
	if req.ImageMIME != nil {
		in.ImageMIME = *req.ImageMIME
	}

	result, err := h.foodUC.Resolve(c.Request.Context(), in)
	if err != nil {
		healthError(c, err)
		return
	}
	c.JSON(http.StatusOK, result)
}

// CreateMeal godoc
//
//	@Summary		Create a meal log
//	@Tags			health
//	@Accept			json
//	@Produce		json
//	@Security		BearerAuth
//	@Param			body	body		CreateMealRequest	true	"Meal payload"
//	@Success		201		{object}	MealLogResponse
//	@Failure		400		{object}	ErrorResponse
//	@Failure		401		{object}	ErrorResponse
//	@Failure		500		{object}	ErrorResponse
//	@Router			/health/meals [post]
func (h *HealthHandler) CreateMeal(c *gin.Context) {
	userID, ok := parseUserID(c)
	if !ok {
		return
	}

	var req CreateMealRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	unit, ok := parseFoodUnit(req.Unit)
	if !ok {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid unit"})
		return
	}

	in := usecase.CreateMealInput{
		UserID:   userID,
		RawInput: req.RawInput,
		Quantity: req.Quantity,
		Unit:     unit,
		Kcal:     req.Kcal,
		ProteinG: req.ProteinG,
		CarbG:    req.CarbG,
		FatG:     req.FatG,
		MealType: req.MealType,
	}
	if req.FoodCacheID != nil && strings.TrimSpace(*req.FoodCacheID) != "" {
		id, err := uuid.Parse(*req.FoodCacheID)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "invalid food_cache_id"})
			return
		}
		in.FoodCacheID = &id
	}
	if req.LoggedAt != nil && strings.TrimSpace(*req.LoggedAt) != "" {
		t, err := time.Parse(time.RFC3339, *req.LoggedAt)
		if err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "logged_at must be RFC3339"})
			return
		}
		in.LoggedAt = &t
	}

	meal, err := h.mealUC.Create(c.Request.Context(), in)
	if err != nil {
		healthError(c, err)
		return
	}
	c.JSON(http.StatusCreated, mealLogResponse(meal))
}

// ListMeals godoc
//
//	@Summary		List meals for a date
//	@Tags			health
//	@Produce		json
//	@Security		BearerAuth
//	@Param			date	query		string	true	"YYYY-MM-DD"
//	@Success		200		{array}		MealLogResponse
//	@Failure		400		{object}	ErrorResponse
//	@Failure		401		{object}	ErrorResponse
//	@Failure		500		{object}	ErrorResponse
//	@Router			/health/meals [get]
func (h *HealthHandler) ListMeals(c *gin.Context) {
	userID, ok := parseUserID(c)
	if !ok {
		return
	}
	day, ok := parseDateQuery(c)
	if !ok {
		return
	}

	meals, err := h.mealUC.List(c.Request.Context(), userID, day)
	if err != nil {
		healthError(c, err)
		return
	}
	out := make([]MealLogResponse, 0, len(meals))
	for i := range meals {
		out = append(out, mealLogResponse(&meals[i]))
	}
	c.JSON(http.StatusOK, out)
}

// DeleteMeal godoc
//
//	@Summary		Delete a meal log
//	@Tags			health
//	@Security		BearerAuth
//	@Param			id	path	string	true	"Meal ID"
//	@Success		204
//	@Failure		400	{object}	ErrorResponse
//	@Failure		401	{object}	ErrorResponse
//	@Failure		404	{object}	ErrorResponse
//	@Failure		500	{object}	ErrorResponse
//	@Router			/health/meals/{id} [delete]
func (h *HealthHandler) DeleteMeal(c *gin.Context) {
	userID, ok := parseUserID(c)
	if !ok {
		return
	}
	id, err := uuid.Parse(c.Param("id"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid meal id"})
		return
	}

	if err := h.mealUC.Delete(c.Request.Context(), userID, id); err != nil {
		healthError(c, err)
		return
	}
	c.Status(http.StatusNoContent)
}

// TodayCheck godoc
//
//	@Summary		Today meal summary
//	@Tags			health
//	@Produce		json
//	@Security		BearerAuth
//	@Param			date	query		string	true	"YYYY-MM-DD"
//	@Success		200		{object}	domain.DayMealSummary
//	@Failure		400		{object}	ErrorResponse
//	@Failure		401		{object}	ErrorResponse
//	@Failure		500		{object}	ErrorResponse
//	@Router			/health/check/today [get]
func (h *HealthHandler) TodayCheck(c *gin.Context) {
	userID, ok := parseUserID(c)
	if !ok {
		return
	}
	day, ok := parseDateQuery(c)
	if !ok {
		return
	}

	sum, err := h.mealUC.TodaySummary(c.Request.Context(), userID, day)
	if err != nil {
		healthError(c, err)
		return
	}
	c.JSON(http.StatusOK, sum)
}

type ResolveFoodRequest struct {
	Text        string   `json:"text"`
	Quantity    *float64 `json:"quantity"`
	Unit        *string  `json:"unit"`
	ImageBase64 *string  `json:"image_base64"`
	ImageMIME   *string  `json:"image_mime"`
}

type CreateMealRequest struct {
	RawInput    string  `json:"raw_input"`
	FoodCacheID *string `json:"food_cache_id"`
	Quantity    float64 `json:"quantity"`
	Unit        string  `json:"unit"`
	Kcal        float64 `json:"kcal"`
	ProteinG    float64 `json:"protein_g"`
	CarbG       float64 `json:"carb_g"`
	FatG        float64 `json:"fat_g"`
	MealType    *string `json:"meal_type"`
	LoggedAt    *string `json:"logged_at"`
}

type MealLogResponse struct {
	ID       string  `json:"id"`
	RawInput string  `json:"raw_input"`
	Quantity float64 `json:"quantity"`
	Unit     string  `json:"unit"`
	Kcal     float64 `json:"kcal"`
	ProteinG float64 `json:"protein_g"`
	CarbG    float64 `json:"carb_g"`
	FatG     float64 `json:"fat_g"`
	MealType *string `json:"meal_type,omitempty"`
	LoggedAt string  `json:"logged_at"`
}
