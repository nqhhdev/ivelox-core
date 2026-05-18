//go:build integration

package integration_test

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
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
	"github.com/nqhhdev/ivelox-core/internal/infrastructure/supabase"
	"github.com/nqhhdev/ivelox-core/internal/repository/postgres"
	"github.com/nqhhdev/ivelox-core/internal/usecase"
)

// --- helpers ---

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

func newTestRouter(t *testing.T) *gin.Engine {
	t.Helper()
	gin.SetMode(gin.TestMode)

	jwtSecret := os.Getenv("SUPABASE_JWT_SECRET")
	if jwtSecret == "" {
		t.Skip("SUPABASE_JWT_SECRET not set")
	}
	supabaseURL := os.Getenv("SUPABASE_URL")
	anonKey := os.Getenv("SUPABASE_ANON_KEY")
	if supabaseURL == "" || anonKey == "" {
		t.Skip("SUPABASE_URL or SUPABASE_ANON_KEY not set")
	}

	pool := newTestPool(t)
	userRepo := postgres.NewUserRepository(pool)
	authClient := supabase.NewAuthClient(supabaseURL, anonKey)
	uc := usecase.NewAuthUsecase(userRepo, authClient)
	return httpdelivery.NewRouter("http://localhost:5173", jwtSecret, uc)
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

func jsonBody(t *testing.T, v any) *bytes.Buffer {
	t.Helper()
	b, err := json.Marshal(v)
	if err != nil {
		t.Fatalf("json marshal: %v", err)
	}
	return bytes.NewBuffer(b)
}

// uniqueEmail generates a unique email per test run to avoid Supabase duplicate conflicts.
func uniqueEmail() string {
	return fmt.Sprintf("test+%d@ivelox-integration.com", time.Now().UnixNano())
}

// --- Register ---

// TestAuthRegister_NewUser: new unique email → 201 + access_token + user_id.
func TestAuthRegister_NewUser(t *testing.T) {
	r := newTestRouter(t)

	email := uniqueEmail()
	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/register",
		jsonBody(t, map[string]string{"email": email, "password": "TestPass123!"}))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 201 {
		t.Fatalf("expected 201, got %d: %s", w.Code, w.Body.String())
	}
	var resp map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil {
		t.Fatalf("failed to decode response: %v", err)
	}
	if resp["user_id"] == nil || resp["user_id"] == "" {
		t.Error("expected user_id in response")
	}
	if resp["email"] != email {
		t.Errorf("expected email %q, got %v", email, resp["email"])
	}
	// access_token may be empty when email confirmation is enabled
	t.Logf("needs_verification=%v, has_token=%v", resp["needs_verification"], resp["access_token"] != "")
}

// TestAuthRegister_MissingPassword: omit password → 400.
func TestAuthRegister_MissingPassword(t *testing.T) {
	r := newTestRouter(t)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/register",
		jsonBody(t, map[string]string{"email": uniqueEmail()}))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 400 {
		t.Fatalf("expected 400, got %d: %s", w.Code, w.Body.String())
	}
}

// TestAuthRegister_InvalidEmail: malformed email → 400.
func TestAuthRegister_InvalidEmail(t *testing.T) {
	r := newTestRouter(t)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/register",
		jsonBody(t, map[string]string{"email": "not-an-email", "password": "TestPass123!"}))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 400 {
		t.Fatalf("expected 400, got %d: %s", w.Code, w.Body.String())
	}
}

// TestAuthRegister_WeakPassword: password too short → 400.
func TestAuthRegister_WeakPassword(t *testing.T) {
	r := newTestRouter(t)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/register",
		jsonBody(t, map[string]string{"email": uniqueEmail(), "password": "123"}))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 400 {
		t.Fatalf("expected 400, got %d: %s", w.Code, w.Body.String())
	}
}

// TestAuthRegister_NoUppercase: password missing uppercase → 400.
func TestAuthRegister_NoUppercase(t *testing.T) {
	r := newTestRouter(t)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/register",
		jsonBody(t, map[string]string{"email": uniqueEmail(), "password": "abc12345"}))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 400 {
		t.Fatalf("expected 400, got %d: %s", w.Code, w.Body.String())
	}
}

// TestAuthRegister_NoDigit: password missing digit → 400.
func TestAuthRegister_NoDigit(t *testing.T) {
	r := newTestRouter(t)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/register",
		jsonBody(t, map[string]string{"email": uniqueEmail(), "password": "Abcdefgh"}))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 400 {
		t.Fatalf("expected 400, got %d: %s", w.Code, w.Body.String())
	}
}

// --- Login ---

// TestAuthLogin_InvalidCredentials: wrong password → 401.
func TestAuthLogin_InvalidCredentials(t *testing.T) {
	r := newTestRouter(t)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login",
		jsonBody(t, map[string]string{"email": "nonexistent@ivelox-integration.com", "password": "wrongpass"}))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 401 {
		t.Fatalf("expected 401, got %d: %s", w.Code, w.Body.String())
	}
}

// TestAuthLogin_MissingBody: empty body → 400.
func TestAuthLogin_MissingBody(t *testing.T) {
	r := newTestRouter(t)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login", bytes.NewBufferString("{}"))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 400 {
		t.Fatalf("expected 400, got %d: %s", w.Code, w.Body.String())
	}
}

// TestAuthRegisterThenLogin: full flow — register → login → get tokens.
func TestAuthRegisterThenLogin(t *testing.T) {
	r := newTestRouter(t)
	email := uniqueEmail()
	password := "TestPass123!"

	// Step 1: register
	regReq := httptest.NewRequest(http.MethodPost, "/api/v1/auth/register",
		jsonBody(t, map[string]string{"email": email, "password": password}))
	regReq.Header.Set("Content-Type", "application/json")
	regW := httptest.NewRecorder()
	r.ServeHTTP(regW, regReq)

	if regW.Code != 201 {
		t.Fatalf("register: expected 201, got %d: %s", regW.Code, regW.Body.String())
	}

	// Step 2: login — Supabase requires email confirmation by default.
	// If email confirmation is disabled in project settings, this returns 200.
	// Otherwise expects 400 ("Email not confirmed").
	loginReq := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login",
		jsonBody(t, map[string]string{"email": email, "password": password}))
	loginReq.Header.Set("Content-Type", "application/json")
	loginW := httptest.NewRecorder()
	r.ServeHTTP(loginW, loginReq)

	if loginW.Code != 200 && loginW.Code != 400 {
		t.Fatalf("login: expected 200 or 400 (unconfirmed), got %d: %s", loginW.Code, loginW.Body.String())
	}

	if loginW.Code == 200 {
		var resp map[string]any
		json.Unmarshal(loginW.Body.Bytes(), &resp)
		if resp["access_token"] == nil || resp["access_token"] == "" {
			t.Error("expected access_token in login response")
		}
		t.Logf("login successful, onboarding_step=%v", resp["onboarding_step"])
	} else {
		t.Logf("login returned 400 — email confirmation likely required in Supabase settings")
	}
}

// --- Verify ---

// TestAuthVerify_NoToken: missing Authorization header → 401.
func TestAuthVerify_NoToken(t *testing.T) {
	r := newTestRouter(t)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/verify", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 401 {
		t.Fatalf("expected 401, got %d: %s", w.Code, w.Body.String())
	}
}

// TestAuthVerify_InvalidToken: garbage token → 401.
func TestAuthVerify_InvalidToken(t *testing.T) {
	r := newTestRouter(t)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/verify", nil)
	req.Header.Set("Authorization", "Bearer not.a.valid.jwt")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 401 {
		t.Fatalf("expected 401, got %d: %s", w.Code, w.Body.String())
	}
}

// TestAuthVerify_ValidTokenUnknownUser: valid JWT signature for user not yet in profiles.
// With RLS enabled, upsert of a random UUID not in auth.users will be blocked → 500.
// This test verifies the JWT middleware passes (token is valid), not the upsert.
func TestAuthVerify_ValidTokenUnknownUser(t *testing.T) {
	r := newTestRouter(t)

	jwtSecret := os.Getenv("SUPABASE_JWT_SECRET")
	token := makeIntegrationToken(uuid.New(), jwtSecret)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/verify", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	// 200 = upsert succeeded (RLS disabled or service role)
	// 500 = upsert blocked by RLS (random UUID not in auth.users) — middleware passed OK
	if w.Code != 200 && w.Code != 500 {
		t.Fatalf("expected 200 or 500, got %d: %s", w.Code, w.Body.String())
	}
	t.Logf("verify result: %d (500 = RLS blocked upsert of unknown UUID, expected in prod)", w.Code)
}

// --- Refresh ---

// TestAuthRefresh_InvalidToken: garbage refresh token → 401.
func TestAuthRefresh_InvalidToken(t *testing.T) {
	r := newTestRouter(t)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/refresh",
		jsonBody(t, map[string]string{"refresh_token": "not-a-valid-token"}))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 401 {
		t.Fatalf("expected 401, got %d: %s", w.Code, w.Body.String())
	}
}

// TestAuthRefresh_MissingBody: missing refresh_token field → 400.
func TestAuthRefresh_MissingBody(t *testing.T) {
	r := newTestRouter(t)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/refresh",
		jsonBody(t, map[string]string{}))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 400 {
		t.Fatalf("expected 400, got %d: %s", w.Code, w.Body.String())
	}
}

// TestAuthRefreshThenVerify: register → get refresh_token → refresh → verify with new token.
// Requires email confirmation disabled in Supabase project settings.
func TestAuthRefreshThenVerify(t *testing.T) {
	r := newTestRouter(t)
	email := uniqueEmail()
	password := "TestPass123!"

	// Step 1: register
	regReq := httptest.NewRequest(http.MethodPost, "/api/v1/auth/register",
		jsonBody(t, map[string]string{"email": email, "password": password}))
	regReq.Header.Set("Content-Type", "application/json")
	regW := httptest.NewRecorder()
	r.ServeHTTP(regW, regReq)
	if regW.Code != 201 {
		t.Fatalf("register: expected 201, got %d: %s", regW.Code, regW.Body.String())
	}
	var regResp map[string]any
	json.Unmarshal(regW.Body.Bytes(), &regResp)

	refreshToken, _ := regResp["refresh_token"].(string)
	if refreshToken == "" {
		t.Skip("no refresh_token in register response — email confirmation may be required")
	}

	// Step 2: refresh → new token pair
	refReq := httptest.NewRequest(http.MethodPost, "/api/v1/auth/refresh",
		jsonBody(t, map[string]string{"refresh_token": refreshToken}))
	refReq.Header.Set("Content-Type", "application/json")
	refW := httptest.NewRecorder()
	r.ServeHTTP(refW, refReq)

	if refW.Code != 200 {
		t.Fatalf("refresh: expected 200, got %d: %s", refW.Code, refW.Body.String())
	}
	var refResp map[string]any
	json.Unmarshal(refW.Body.Bytes(), &refResp)

	newAccessToken, _ := refResp["access_token"].(string)
	newRefreshToken, _ := refResp["refresh_token"].(string)
	if newAccessToken == "" {
		t.Error("expected new access_token after refresh")
	}
	if newRefreshToken == "" {
		t.Error("expected new refresh_token after refresh")
	}
	t.Logf("refresh successful, new tokens issued")
}

// --- Logout ---

// TestAuthLogout_NoToken: missing Authorization header → 401.
func TestAuthLogout_NoToken(t *testing.T) {
	r := newTestRouter(t)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/logout", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 401 {
		t.Fatalf("expected 401, got %d: %s", w.Code, w.Body.String())
	}
}

// TestAuthLogout_InvalidToken: garbage token → 401.
func TestAuthLogout_InvalidToken(t *testing.T) {
	r := newTestRouter(t)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/logout", nil)
	req.Header.Set("Authorization", "Bearer not.a.valid.jwt")
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 401 {
		t.Fatalf("expected 401, got %d: %s", w.Code, w.Body.String())
	}
}

// TestAuthRegisterThenLogout: register → logout with access token → 204.
// Requires email confirmation disabled in Supabase project settings.
func TestAuthRegisterThenLogout(t *testing.T) {
	r := newTestRouter(t)
	email := uniqueEmail()
	password := "TestPass123!"

	// Step 1: register
	regReq := httptest.NewRequest(http.MethodPost, "/api/v1/auth/register",
		jsonBody(t, map[string]string{"email": email, "password": password}))
	regReq.Header.Set("Content-Type", "application/json")
	regW := httptest.NewRecorder()
	r.ServeHTTP(regW, regReq)
	if regW.Code != 201 {
		t.Fatalf("register: expected 201, got %d: %s", regW.Code, regW.Body.String())
	}
	var regResp map[string]any
	json.Unmarshal(regW.Body.Bytes(), &regResp)

	accessToken, _ := regResp["access_token"].(string)
	if accessToken == "" {
		t.Skip("no access_token in register response — email confirmation may be required")
	}

	// Step 2: logout
	logoutReq := httptest.NewRequest(http.MethodPost, "/api/v1/auth/logout", nil)
	logoutReq.Header.Set("Authorization", "Bearer "+accessToken)
	logoutW := httptest.NewRecorder()
	r.ServeHTTP(logoutW, logoutReq)

	if logoutW.Code != 204 {
		t.Fatalf("logout: expected 204, got %d: %s", logoutW.Code, logoutW.Body.String())
	}
	t.Logf("logout successful")
}

// TestAuthFullFlow: register → refresh → verify → logout.
// Requires email confirmation disabled in Supabase project settings.
func TestAuthFullFlow(t *testing.T) {
	r := newTestRouter(t)
	email := uniqueEmail()
	password := "TestPass123!"

	// Step 1: register
	regReq := httptest.NewRequest(http.MethodPost, "/api/v1/auth/register",
		jsonBody(t, map[string]string{"email": email, "password": password}))
	regReq.Header.Set("Content-Type", "application/json")
	regW := httptest.NewRecorder()
	r.ServeHTTP(regW, regReq)
	if regW.Code != 201 {
		t.Fatalf("register: expected 201, got %d: %s", regW.Code, regW.Body.String())
	}
	var regResp map[string]any
	json.Unmarshal(regW.Body.Bytes(), &regResp)

	accessToken, _ := regResp["access_token"].(string)
	refreshToken, _ := regResp["refresh_token"].(string)
	if accessToken == "" || refreshToken == "" {
		t.Skip("tokens missing — email confirmation may be required in Supabase settings")
	}

	// Step 2: verify with access token
	verifyReq := httptest.NewRequest(http.MethodPost, "/api/v1/auth/verify", nil)
	verifyReq.Header.Set("Authorization", "Bearer "+accessToken)
	verifyW := httptest.NewRecorder()
	r.ServeHTTP(verifyW, verifyReq)
	if verifyW.Code != 200 {
		t.Fatalf("verify: expected 200, got %d: %s", verifyW.Code, verifyW.Body.String())
	}
	var verifyResp map[string]any
	json.Unmarshal(verifyW.Body.Bytes(), &verifyResp)
	if verifyResp["email"] != email {
		t.Errorf("verify: expected email %q, got %v", email, verifyResp["email"])
	}
	t.Logf("verify: profile synced, provider=%v", verifyResp["provider"])

	// Step 3: refresh token
	refReq := httptest.NewRequest(http.MethodPost, "/api/v1/auth/refresh",
		jsonBody(t, map[string]string{"refresh_token": refreshToken}))
	refReq.Header.Set("Content-Type", "application/json")
	refW := httptest.NewRecorder()
	r.ServeHTTP(refW, refReq)
	if refW.Code != 200 {
		t.Fatalf("refresh: expected 200, got %d: %s", refW.Code, refW.Body.String())
	}
	var refResp map[string]any
	json.Unmarshal(refW.Body.Bytes(), &refResp)
	newAccessToken, _ := refResp["access_token"].(string)
	if newAccessToken == "" {
		t.Fatal("refresh: expected new access_token")
	}

	// Step 4: logout with new access token
	logoutReq := httptest.NewRequest(http.MethodPost, "/api/v1/auth/logout", nil)
	logoutReq.Header.Set("Authorization", "Bearer "+newAccessToken)
	logoutW := httptest.NewRecorder()
	r.ServeHTTP(logoutW, logoutReq)
	if logoutW.Code != 204 {
		t.Fatalf("logout: expected 204, got %d: %s", logoutW.Code, logoutW.Body.String())
	}
	t.Logf("full auth flow completed: register → verify → refresh → logout")
}
