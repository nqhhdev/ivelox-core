//go:build integration

package integration_test

import (
	"context"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	httpdelivery "github.com/nqhhdev/ivelox-core/internal/delivery/http"
	"github.com/nqhhdev/ivelox-core/internal/repository/postgres"
	"github.com/nqhhdev/ivelox-core/internal/usecase"
)

func newTestPool(t *testing.T) *pgxpool.Pool {
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
	return pool
}

func makeIntegrationToken(userID uuid.UUID, secret string) string {
	claims := jwt.MapClaims{
		"sub":   userID.String(),
		"email": "integration@test.com",
		"exp":   time.Now().Add(time.Hour).Unix(),
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	signed, _ := token.SignedString([]byte(secret))
	return signed
}

func TestAuthVerify_RealDB_UserNotFound(t *testing.T) {
	gin.SetMode(gin.TestMode)
	pool := newTestPool(t)

	jwtSecret := os.Getenv("SUPABASE_JWT_SECRET")
	if jwtSecret == "" {
		t.Skip("SUPABASE_JWT_SECRET not set")
	}

	userRepo := postgres.NewUserRepository(pool)
	uc := usecase.NewAuthUsecase(userRepo)
	r := httpdelivery.NewRouter("http://localhost:5173", jwtSecret, uc)

	// Use a random UUID that doesn't exist in DB
	unknownID := uuid.New()
	token := makeIntegrationToken(unknownID, jwtSecret)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/verify", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	// Should return 404 since user doesn't exist in profiles table
	if w.Code != 404 {
		t.Fatalf("expected 404 for unknown user, got %d: %s", w.Code, w.Body.String())
	}
}
