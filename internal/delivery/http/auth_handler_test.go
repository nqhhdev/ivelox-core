package http_test

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
	httpdelivery "github.com/nqhhdev/ivelox-core/internal/delivery/http"
	"github.com/nqhhdev/ivelox-core/internal/domain"
	"github.com/nqhhdev/ivelox-core/internal/middleware"
	"github.com/nqhhdev/ivelox-core/internal/usecase"
)

type fakeUserRepo struct {
	users map[uuid.UUID]*domain.User
}

func (f *fakeUserRepo) GetByID(id uuid.UUID) (*domain.User, error) {
	u, ok := f.users[id]
	if !ok {
		return nil, fmt.Errorf("not found")
	}
	return u, nil
}

const secret = "test-secret-key-that-is-long-enough"

func makeTestToken(userID uuid.UUID) string {
	claims := jwt.MapClaims{
		"sub":   userID.String(),
		"email": "test@example.com",
		"exp":   time.Now().Add(time.Hour).Unix(),
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	signed, _ := token.SignedString([]byte(secret))
	return signed
}

func TestVerifyHandler_ReturnsProfile(t *testing.T) {
	gin.SetMode(gin.TestMode)

	userID := uuid.New()
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{
		userID: {ID: userID, DisplayName: "Test User", Role: "user"},
	}}
	uc := usecase.NewAuthUsecase(repo)
	handler := httpdelivery.NewAuthHandler(uc)

	r := gin.New()
	r.Use(middleware.Auth(secret))
	r.POST("/api/v1/auth/verify", handler.Verify)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/verify", nil)
	req.Header.Set("Authorization", "Bearer "+makeTestToken(userID))
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 200 {
		t.Fatalf("expected 200, got %d: %s", w.Code, w.Body.String())
	}

	var body map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &body)
	if body["display_name"] != "Test User" {
		t.Errorf("expected display_name 'Test User', got %v", body["display_name"])
	}
}
