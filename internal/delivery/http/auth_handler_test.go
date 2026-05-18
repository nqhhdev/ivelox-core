package http_test

import (
	"bytes"
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

// --- fakes ---

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

func (f *fakeUserRepo) Upsert(u *domain.User) error {
	if f.users == nil {
		f.users = map[uuid.UUID]*domain.User{}
	}
	f.users[u.ID] = u
	return nil
}

type fakeAuthProvider struct {
	signUpErr error
	signInErr error
}

func (f *fakeAuthProvider) SignUp(email, password string) (*domain.AuthResult, error) {
	if f.signUpErr != nil {
		return nil, f.signUpErr
	}
	return &domain.AuthResult{
		AccessToken:  "access-tok",
		RefreshToken: "refresh-tok",
		UserID:       "00000000-0000-0000-0000-000000000001",
		Email:        email,
	}, nil
}

func (f *fakeAuthProvider) SignIn(email, password string) (*domain.AuthResult, error) {
	if f.signInErr != nil {
		return nil, f.signInErr
	}
	return &domain.AuthResult{
		AccessToken:  "access-tok",
		RefreshToken: "refresh-tok",
		UserID:       "00000000-0000-0000-0000-000000000001",
		Email:        email,
	}, nil
}

// --- helpers ---

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

func setupAuthRouter(repo domain.UserRepository, auth domain.AuthProvider) *gin.Engine {
	gin.SetMode(gin.TestMode)
	uc := usecase.NewAuthUsecase(repo, auth)
	handler := httpdelivery.NewAuthHandler(uc)
	r := gin.New()
	// public
	r.POST("/api/v1/auth/register", handler.Register)
	r.POST("/api/v1/auth/login", handler.Login)
	// protected
	protected := r.Group("")
	protected.Use(middleware.Auth(secret))
	protected.POST("/api/v1/auth/verify", handler.Verify)
	return r
}

func jsonBody(t *testing.T, v any) *bytes.Buffer {
	t.Helper()
	b, err := json.Marshal(v)
	if err != nil {
		t.Fatalf("marshal body: %v", err)
	}
	return bytes.NewBuffer(b)
}

// --- Verify ---

func TestVerifyHandler_ReturnsProfile(t *testing.T) {
	userID := uuid.New()
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{
		userID: {ID: userID, DisplayName: "Test User", Role: "user"},
	}}
	r := setupAuthRouter(repo, &fakeAuthProvider{})

	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/verify", nil)
	req.Header.Set("Authorization", "Bearer "+makeTestToken(userID))
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 200 {
		t.Fatalf("expected 200, got %d: %s", w.Code, w.Body.String())
	}
	var body map[string]any
	json.Unmarshal(w.Body.Bytes(), &body)
	if body["display_name"] != "Test User" {
		t.Errorf("expected display_name 'Test User', got %v", body["display_name"])
	}
}

// TestVerifyHandler_NewUser: valid JWT for user not yet in DB → 200, profile created via upsert (handles Google OAuth first login).
func TestVerifyHandler_NewUser(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	r := setupAuthRouter(repo, &fakeAuthProvider{})

	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/verify", nil)
	req.Header.Set("Authorization", "Bearer "+makeTestToken(uuid.New()))
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 200 {
		t.Fatalf("expected 200 (upsert creates new user), got %d: %s", w.Code, w.Body.String())
	}
}

func TestVerifyHandler_MissingToken(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	r := setupAuthRouter(repo, &fakeAuthProvider{})

	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/verify", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 401 {
		t.Fatalf("expected 401, got %d: %s", w.Code, w.Body.String())
	}
}

// --- Register ---

func TestRegisterHandler_Success(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	r := setupAuthRouter(repo, &fakeAuthProvider{})

	body := jsonBody(t, map[string]string{"email": "new@example.com", "password": "secret123"})
	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/register", body)
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 201 {
		t.Fatalf("expected 201, got %d: %s", w.Code, w.Body.String())
	}
	var resp map[string]any
	json.Unmarshal(w.Body.Bytes(), &resp)
	if resp["access_token"] == "" || resp["access_token"] == nil {
		t.Error("expected access_token in response")
	}
	if resp["email"] != "new@example.com" {
		t.Errorf("expected email 'new@example.com', got %v", resp["email"])
	}
}

func TestRegisterHandler_InvalidBody(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	r := setupAuthRouter(repo, &fakeAuthProvider{})

	// missing password
	body := jsonBody(t, map[string]string{"email": "bad@example.com"})
	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/register", body)
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 400 {
		t.Fatalf("expected 400, got %d: %s", w.Code, w.Body.String())
	}
}

func TestRegisterHandler_AuthProviderError(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	r := setupAuthRouter(repo, &fakeAuthProvider{
		signUpErr: fmt.Errorf("email already registered"),
	})

	body := jsonBody(t, map[string]string{"email": "dup@example.com", "password": "secret123"})
	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/register", body)
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 400 {
		t.Fatalf("expected 400, got %d: %s", w.Code, w.Body.String())
	}
}

// --- Login ---

func TestLoginHandler_Success(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	r := setupAuthRouter(repo, &fakeAuthProvider{})

	body := jsonBody(t, map[string]string{"email": "user@example.com", "password": "secret123"})
	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login", body)
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 200 {
		t.Fatalf("expected 200, got %d: %s", w.Code, w.Body.String())
	}
	var resp map[string]any
	json.Unmarshal(w.Body.Bytes(), &resp)
	if resp["access_token"] == "" || resp["access_token"] == nil {
		t.Error("expected access_token in response")
	}
}

func TestLoginHandler_InvalidCredentials(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	r := setupAuthRouter(repo, &fakeAuthProvider{
		signInErr: fmt.Errorf("invalid login credentials"),
	})

	body := jsonBody(t, map[string]string{"email": "user@example.com", "password": "wrong"})
	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login", body)
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 401 {
		t.Fatalf("expected 401, got %d: %s", w.Code, w.Body.String())
	}
}

func TestLoginHandler_InvalidBody(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	r := setupAuthRouter(repo, &fakeAuthProvider{})

	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login", bytes.NewBufferString("not-json"))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 400 {
		t.Fatalf("expected 400, got %d: %s", w.Code, w.Body.String())
	}
}
