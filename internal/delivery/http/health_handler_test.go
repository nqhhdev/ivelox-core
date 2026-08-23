package http_test

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	httpdelivery "github.com/nqhhdev/ivelox-core/internal/delivery/http"
	"github.com/nqhhdev/ivelox-core/internal/domain"
	"github.com/nqhhdev/ivelox-core/internal/health"
	"github.com/nqhhdev/ivelox-core/internal/middleware"
	"github.com/nqhhdev/ivelox-core/internal/usecase"
)

type fakeFoodResolveUC struct {
	result *domain.ResolveResult
	err    error
	last   usecase.FoodResolveInput
}

func (f *fakeFoodResolveUC) Resolve(_ context.Context, in usecase.FoodResolveInput) (*domain.ResolveResult, error) {
	f.last = in
	if f.err != nil {
		return nil, f.err
	}
	return f.result, nil
}

type fakeMealUC struct {
	created    *domain.MealLog
	createErr  error
	listed     []domain.MealLog
	listErr    error
	deleteErr  error
	summary    *domain.DayMealSummary
	sumErr     error
	lastCreate usecase.CreateMealInput
	lastDay    time.Time
	lastUser   uuid.UUID
	lastID     uuid.UUID
}

func (f *fakeMealUC) Create(_ context.Context, in usecase.CreateMealInput) (*domain.MealLog, error) {
	f.lastCreate = in
	if f.createErr != nil {
		return nil, f.createErr
	}
	if f.created != nil {
		return f.created, nil
	}
	return &domain.MealLog{
		ID:       uuid.New(),
		UserID:   in.UserID,
		RawInput: in.RawInput,
		Quantity: in.Quantity,
		Unit:     in.Unit,
		Kcal:     in.Kcal,
		ProteinG: in.ProteinG,
		CarbG:    in.CarbG,
		FatG:     in.FatG,
		MealType: in.MealType,
		LoggedAt: time.Date(2026, 8, 23, 12, 0, 0, 0, time.UTC),
	}, nil
}

func (f *fakeMealUC) List(_ context.Context, userID uuid.UUID, day time.Time) ([]domain.MealLog, error) {
	f.lastUser = userID
	f.lastDay = day
	if f.listErr != nil {
		return nil, f.listErr
	}
	if f.listed == nil {
		return []domain.MealLog{}, nil
	}
	return f.listed, nil
}

func (f *fakeMealUC) Delete(_ context.Context, userID, id uuid.UUID) error {
	f.lastUser = userID
	f.lastID = id
	return f.deleteErr
}

func (f *fakeMealUC) TodaySummary(_ context.Context, userID uuid.UUID, day time.Time) (*domain.DayMealSummary, error) {
	f.lastUser = userID
	f.lastDay = day
	if f.sumErr != nil {
		return nil, f.sumErr
	}
	if f.summary != nil {
		return f.summary, nil
	}
	return &domain.DayMealSummary{}, nil
}

func setupHealthRouter(food *fakeFoodResolveUC, meal *fakeMealUC) *gin.Engine {
	gin.SetMode(gin.TestMode)
	h := httpdelivery.NewHealthHandler(food, meal)
	r := gin.New()
	protected := r.Group("/api/v1")
	protected.Use(middleware.Auth(secret))
	protected.POST("/health/foods/resolve", h.ResolveFood)
	protected.POST("/health/meals", h.CreateMeal)
	protected.GET("/health/meals", h.ListMeals)
	protected.DELETE("/health/meals/:id", h.DeleteMeal)
	protected.GET("/health/check/today", h.TodayCheck)
	return r
}

func authReq(t *testing.T, method, path string, body any, userID uuid.UUID) *http.Request {
	t.Helper()
	var req *http.Request
	if body != nil {
		req = httptest.NewRequest(method, path, jsonBody(t, body))
		req.Header.Set("Content-Type", "application/json")
	} else {
		req = httptest.NewRequest(method, path, nil)
	}
	req.Header.Set("Authorization", "Bearer "+makeTestToken(userID))
	return req
}

func TestResolveFood_SuccessShape(t *testing.T) {
	food := &fakeFoodResolveUC{
		result: &domain.ResolveResult{
			Items: []domain.FoodItem{{
				Name:       "pho bo",
				Quantity:   1,
				Unit:       domain.UnitServing,
				Kcal:       450,
				ProteinG:   25,
				CarbG:      50,
				FatG:       12,
				Confidence: 0.9,
			}},
			Source: "cache",
		},
	}
	r := setupHealthRouter(food, &fakeMealUC{})
	userID := uuid.New()
	qty := 1.0

	req := authReq(t, http.MethodPost, "/api/v1/health/foods/resolve", map[string]any{
		"text":     "pho bo",
		"quantity": qty,
		"unit":     "serving",
	}, userID)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 200 {
		t.Fatalf("expected 200, got %d: %s", w.Code, w.Body.String())
	}
	var body map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if body["source"] != "cache" {
		t.Errorf("source = %v, want cache", body["source"])
	}
	items, ok := body["items"].([]any)
	if !ok || len(items) != 1 {
		t.Fatalf("items = %v, want 1 item", body["items"])
	}
	item, _ := items[0].(map[string]any)
	if item["name"] != "pho bo" || item["kcal"] != 450.0 || item["protein_g"] != 25.0 {
		t.Errorf("unexpected item: %v", item)
	}
	if food.last.Text != "pho bo" || food.last.Quantity == nil || *food.last.Quantity != 1 {
		t.Errorf("unexpected resolve input: %+v", food.last)
	}
}

func TestResolveFood_Unavailable(t *testing.T) {
	food := &fakeFoodResolveUC{err: usecase.ErrUnavailable}
	r := setupHealthRouter(food, &fakeMealUC{})

	req := authReq(t, http.MethodPost, "/api/v1/health/foods/resolve", map[string]any{
		"text": "pho bo",
	}, uuid.New())
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 503 {
		t.Fatalf("expected 503, got %d: %s", w.Code, w.Body.String())
	}
}

func TestResolveFood_EmptyBody(t *testing.T) {
	food := &fakeFoodResolveUC{
		err: fmt.Errorf("%w: text or image is required", usecase.ErrInvalidInput),
	}
	r := setupHealthRouter(food, &fakeMealUC{})

	req := authReq(t, http.MethodPost, "/api/v1/health/foods/resolve", map[string]any{}, uuid.New())
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 400 {
		t.Fatalf("expected 400, got %d: %s", w.Code, w.Body.String())
	}
}

func TestResolveFood_InvalidJSON(t *testing.T) {
	r := setupHealthRouter(&fakeFoodResolveUC{}, &fakeMealUC{})
	req := httptest.NewRequest(http.MethodPost, "/api/v1/health/foods/resolve", jsonBody(t, "not-an-object"))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+makeTestToken(uuid.New()))
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 400 {
		t.Fatalf("expected 400, got %d: %s", w.Code, w.Body.String())
	}
}

func TestCreateMeal_Created(t *testing.T) {
	userID := uuid.New()
	mealID := uuid.New()
	meal := &fakeMealUC{
		created: &domain.MealLog{
			ID:       mealID,
			UserID:   userID,
			RawInput: "pho bo",
			Quantity: 1,
			Unit:     domain.UnitServing,
			Kcal:     450,
			ProteinG: 25,
			CarbG:    50,
			FatG:     12,
			LoggedAt: time.Date(2026, 8, 23, 8, 0, 0, 0, time.UTC),
		},
	}
	r := setupHealthRouter(&fakeFoodResolveUC{}, meal)

	req := authReq(t, http.MethodPost, "/api/v1/health/meals", map[string]any{
		"raw_input": "pho bo",
		"quantity":  1,
		"unit":      "serving",
		"kcal":      450,
		"protein_g": 25,
		"carb_g":    50,
		"fat_g":     12,
	}, userID)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 201 {
		t.Fatalf("expected 201, got %d: %s", w.Code, w.Body.String())
	}
	var body map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if body["id"] != mealID.String() {
		t.Errorf("id = %v, want %s", body["id"], mealID)
	}
	if body["raw_input"] != "pho bo" || body["kcal"] != 450.0 || body["unit"] != "serving" {
		t.Errorf("unexpected meal: %v", body)
	}
	if body["logged_at"] != "2026-08-23T08:00:00Z" {
		t.Errorf("logged_at = %v", body["logged_at"])
	}
	if meal.lastCreate.UserID != userID {
		t.Errorf("userID = %s, want %s", meal.lastCreate.UserID, userID)
	}
}

func TestCreateMeal_InvalidQuantity(t *testing.T) {
	meal := &fakeMealUC{
		createErr: fmt.Errorf("%w: quantity must be > 0", usecase.ErrInvalidInput),
	}
	r := setupHealthRouter(&fakeFoodResolveUC{}, meal)

	req := authReq(t, http.MethodPost, "/api/v1/health/meals", map[string]any{
		"raw_input": "pho",
		"quantity":  0,
		"unit":      "serving",
		"kcal":      100,
	}, uuid.New())
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 400 {
		t.Fatalf("expected 400, got %d: %s", w.Code, w.Body.String())
	}
}

func TestDeleteMeal_NotFound(t *testing.T) {
	meal := &fakeMealUC{deleteErr: fmt.Errorf("meal log not found")}
	r := setupHealthRouter(&fakeFoodResolveUC{}, meal)

	req := authReq(t, http.MethodDelete, "/api/v1/health/meals/"+uuid.New().String(), nil, uuid.New())
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 404 {
		t.Fatalf("expected 404, got %d: %s", w.Code, w.Body.String())
	}
}

func TestListMeals_RequiresDate(t *testing.T) {
	r := setupHealthRouter(&fakeFoodResolveUC{}, &fakeMealUC{})

	req := authReq(t, http.MethodGet, "/api/v1/health/meals", nil, uuid.New())
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 400 {
		t.Fatalf("expected 400, got %d: %s", w.Code, w.Body.String())
	}
}

func TestTodayCheck_Success(t *testing.T) {
	meal := &fakeMealUC{
		summary: &domain.DayMealSummary{
			EatenKcal: 500,
			ProteinG:  30,
			CarbG:     40,
			FatG:      10,
			MealCount: 2,
		},
	}
	r := setupHealthRouter(&fakeFoodResolveUC{}, meal)
	userID := uuid.New()

	req := authReq(t, http.MethodGet, "/api/v1/health/check/today?date=2026-08-23", nil, userID)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 200 {
		t.Fatalf("expected 200, got %d: %s", w.Code, w.Body.String())
	}
	var body map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode: %v", err)
	}
	if body["eaten_kcal"] != 500.0 || body["meal_count"] != float64(2) {
		t.Errorf("unexpected summary: %v", body)
	}
	if meal.lastUser != userID {
		t.Errorf("userID = %s, want %s", meal.lastUser, userID)
	}
	wantDay, err := health.ParseCivilDate("2026-08-23")
	if err != nil {
		t.Fatal(err)
	}
	if !meal.lastDay.Equal(wantDay) {
		t.Errorf("day = %v, want %v", meal.lastDay, wantDay)
	}
}

func TestHealthError_InternalDoesNotLeak(t *testing.T) {
	meal := &fakeMealUC{listErr: fmt.Errorf("pq: password authentication failed for user postgres")}
	r := setupHealthRouter(&fakeFoodResolveUC{}, meal)

	req := authReq(t, http.MethodGet, "/api/v1/health/meals?date=2026-08-23", nil, uuid.New())
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 500 {
		t.Fatalf("expected 500, got %d: %s", w.Code, w.Body.String())
	}
	if strings.Contains(w.Body.String(), "password") || strings.Contains(w.Body.String(), "postgres") {
		t.Fatalf("leaked internal error: %s", w.Body.String())
	}
	if !strings.Contains(w.Body.String(), "internal server error") {
		t.Fatalf("body = %s, want internal server error", w.Body.String())
	}
}

func TestResolveFood_ResolverError_503SafeMessage(t *testing.T) {
	food := &fakeFoodResolveUC{err: fmt.Errorf("%w", usecase.ErrUnavailable)}
	r := setupHealthRouter(food, &fakeMealUC{})

	req := authReq(t, http.MethodPost, "/api/v1/health/foods/resolve", map[string]any{
		"text": "pho bo",
	}, uuid.New())
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 503 {
		t.Fatalf("expected 503, got %d: %s", w.Code, w.Body.String())
	}
	if strings.Contains(strings.ToLower(w.Body.String()), "gemini") {
		t.Fatalf("leaked resolver details: %s", w.Body.String())
	}
}

func TestResolveFood_Timeout_503(t *testing.T) {
	food := &fakeFoodResolveUC{err: context.DeadlineExceeded}
	r := setupHealthRouter(food, &fakeMealUC{})

	req := authReq(t, http.MethodPost, "/api/v1/health/foods/resolve", map[string]any{
		"text": "pho bo",
	}, uuid.New())
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 503 {
		t.Fatalf("expected 503, got %d: %s", w.Code, w.Body.String())
	}
}

func TestResolveFood_RejectsUnsupportedMIME(t *testing.T) {
	r := setupHealthRouter(&fakeFoodResolveUC{}, &fakeMealUC{})
	req := authReq(t, http.MethodPost, "/api/v1/health/foods/resolve", map[string]any{
		"text":         "pho",
		"image_base64": "aGVsbG8=",
		"image_mime":   "application/pdf",
	}, uuid.New())
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 400 {
		t.Fatalf("expected 400, got %d: %s", w.Code, w.Body.String())
	}
}

func TestResolveFood_RejectsOversizedImage(t *testing.T) {
	r := setupHealthRouter(&fakeFoodResolveUC{}, &fakeMealUC{})
	raw := make([]byte, 3<<20+1)
	req := authReq(t, http.MethodPost, "/api/v1/health/foods/resolve", map[string]any{
		"text":         "pho",
		"image_base64": base64.StdEncoding.EncodeToString(raw),
		"image_mime":   "image/jpeg",
	}, uuid.New())
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 400 {
		t.Fatalf("expected 400, got %d: %s", w.Code, w.Body.String())
	}
}

func TestCreateMeal_InvalidMealType(t *testing.T) {
	r := setupHealthRouter(&fakeFoodResolveUC{}, &fakeMealUC{})
	req := authReq(t, http.MethodPost, "/api/v1/health/meals", map[string]any{
		"raw_input": "pho",
		"quantity":  1,
		"unit":      "serving",
		"kcal":      100,
		"meal_type": "brunch",
	}, uuid.New())
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 400 {
		t.Fatalf("expected 400, got %d: %s", w.Code, w.Body.String())
	}
}
