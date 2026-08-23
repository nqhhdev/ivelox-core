package middleware_test

import (
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
	"github.com/nqhhdev/ivelox-core/internal/middleware"
)

const testSecret = "test-secret-key-that-is-long-enough"

func makeToken(userID uuid.UUID, secret string, expiry time.Time) string {
	claims := jwt.MapClaims{
		"sub":   userID.String(),
		"email": "test@example.com",
		"exp":   expiry.Unix(),
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	signed, _ := token.SignedString([]byte(secret))
	return signed
}

func setupRouter(secret string) *gin.Engine {
	gin.SetMode(gin.TestMode)
	r := gin.New()
	r.Use(middleware.Auth(secret))
	r.GET("/protected", func(c *gin.Context) {
		c.JSON(200, gin.H{"userID": c.GetString("userID")})
	})
	return r
}

func TestAuthMiddleware_ValidToken(t *testing.T) {
	r := setupRouter(testSecret)
	userID := uuid.New()
	token := makeToken(userID, testSecret, time.Now().Add(time.Hour))

	req := httptest.NewRequest(http.MethodGet, "/protected", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 200 {
		t.Fatalf("expected 200, got %d: %s", w.Code, w.Body.String())
	}
}

func TestAuthMiddleware_MissingToken(t *testing.T) {
	r := setupRouter(testSecret)

	req := httptest.NewRequest(http.MethodGet, "/protected", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 401 {
		t.Fatalf("expected 401, got %d", w.Code)
	}
}

func TestAuthMiddleware_ExpiredToken(t *testing.T) {
	r := setupRouter(testSecret)
	userID := uuid.New()
	token := makeToken(userID, testSecret, time.Now().Add(-time.Hour))

	req := httptest.NewRequest(http.MethodGet, "/protected", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 401 {
		t.Fatalf("expected 401, got %d", w.Code)
	}
}

func TestAuthMiddleware_InvalidSignature(t *testing.T) {
	r := setupRouter(testSecret)
	userID := uuid.New()
	token := makeToken(userID, "wrong-secret-key-that-is-long-enough", time.Now().Add(time.Hour))

	req := httptest.NewRequest(http.MethodGet, "/protected", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 401 {
		t.Fatalf("expected 401, got %d", w.Code)
	}
}

func TestAuthMiddleware_MalformedHeader(t *testing.T) {
	r := setupRouter(testSecret)

	req := httptest.NewRequest(http.MethodGet, "/protected", nil)
	req.Header.Set("Authorization", "Token not-bearer-format")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 401 {
		t.Fatalf("expected 401, got %d", w.Code)
	}
}

func TestAuthMiddleware_MissingSubClaim(t *testing.T) {
	gin.SetMode(gin.TestMode)
	r := gin.New()
	r.Use(middleware.Auth(testSecret))
	r.GET("/protected", func(c *gin.Context) { c.JSON(200, nil) })

	// Token without sub claim
	claims := jwt.MapClaims{
		"email": "test@example.com",
		"exp":   time.Now().Add(time.Hour).Unix(),
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	signed, _ := token.SignedString([]byte(testSecret))

	req := httptest.NewRequest(http.MethodGet, "/protected", nil)
	req.Header.Set("Authorization", "Bearer "+signed)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 401 {
		t.Fatalf("expected 401, got %d", w.Code)
	}
}
