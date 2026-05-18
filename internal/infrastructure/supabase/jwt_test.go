package supabase_test

import (
	"crypto/rand"
	"crypto/rsa"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
	"github.com/nqhhdev/ivelox-core/internal/infrastructure/supabase"
)

const jwtSecret = "test-secret-key-that-is-long-enough"

func makeJWT(sub, email string, secret string, expiry time.Time) string {
	claims := jwt.MapClaims{
		"sub":   sub,
		"email": email,
		"exp":   expiry.Unix(),
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	signed, _ := token.SignedString([]byte(secret))
	return signed
}

func TestVerifyJWT_ValidToken(t *testing.T) {
	userID := uuid.New()
	token := makeJWT(userID.String(), "test@example.com", jwtSecret, time.Now().Add(time.Hour))

	claims, err := supabase.VerifyJWT(token, jwtSecret)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if claims.Sub != userID.String() {
		t.Errorf("expected sub %q, got %q", userID.String(), claims.Sub)
	}
}

func TestVerifyJWT_ExpiredToken(t *testing.T) {
	userID := uuid.New()
	token := makeJWT(userID.String(), "test@example.com", jwtSecret, time.Now().Add(-time.Hour))

	_, err := supabase.VerifyJWT(token, jwtSecret)
	if err == nil {
		t.Fatal("expected error for expired token, got nil")
	}
}

func TestVerifyJWT_WrongSecret(t *testing.T) {
	userID := uuid.New()
	token := makeJWT(userID.String(), "test@example.com", "wrong-secret-key-that-is-long-enough", time.Now().Add(time.Hour))

	_, err := supabase.VerifyJWT(token, jwtSecret)
	if err == nil {
		t.Fatal("expected error for wrong secret, got nil")
	}
}

func TestVerifyJWT_InvalidSubUUID(t *testing.T) {
	token := makeJWT("not-a-uuid", "test@example.com", jwtSecret, time.Now().Add(time.Hour))

	_, err := supabase.VerifyJWT(token, jwtSecret)
	if err == nil {
		t.Fatal("expected error for invalid UUID in sub, got nil")
	}
}

func TestVerifyJWT_MalformedToken(t *testing.T) {
	_, err := supabase.VerifyJWT("this.is.not.a.jwt", jwtSecret)
	if err == nil {
		t.Fatal("expected error for malformed token, got nil")
	}
}

func TestVerifyJWT_GoogleOAuthClaims(t *testing.T) {
	userID := uuid.New()
	claims := jwt.MapClaims{
		"sub":   userID.String(),
		"email": "google@example.com",
		"exp":   time.Now().Add(time.Hour).Unix(),
		"app_metadata": map[string]any{
			"provider":  "google",
			"providers": []string{"google"},
		},
		"user_metadata": map[string]any{
			"avatar_url": "https://lh3.googleusercontent.com/photo.jpg",
			"full_name":  "Google User",
			"name":       "Google User",
		},
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	signed, _ := token.SignedString([]byte(jwtSecret))

	result, err := supabase.VerifyJWT(signed, jwtSecret)
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if result.AppMetadata.Provider != "google" {
		t.Errorf("expected provider 'google', got %q", result.AppMetadata.Provider)
	}
	if result.UserMetadata.AvatarURL != "https://lh3.googleusercontent.com/photo.jpg" {
		t.Errorf("expected avatar_url set, got %q", result.UserMetadata.AvatarURL)
	}
	if result.UserMetadata.FullName != "Google User" {
		t.Errorf("expected full_name 'Google User', got %q", result.UserMetadata.FullName)
	}
}

func TestVerifyJWT_EmptyToken(t *testing.T) {
	_, err := supabase.VerifyJWT("", jwtSecret)
	if err == nil {
		t.Fatal("expected error for empty token, got nil")
	}
}

func TestVerifyJWT_WrongAlgorithm(t *testing.T) {
	// Generate RS256 token to trigger unexpected signing method branch
	privateKey, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatalf("failed to generate RSA key: %v", err)
	}
	claims := jwt.MapClaims{
		"sub":   uuid.New().String(),
		"email": "test@example.com",
		"exp":   time.Now().Add(time.Hour).Unix(),
	}
	token := jwt.NewWithClaims(jwt.SigningMethodRS256, claims)
	signed, err := token.SignedString(privateKey)
	if err != nil {
		t.Fatalf("failed to sign token: %v", err)
	}

	_, verifyErr := supabase.VerifyJWT(signed, jwtSecret)
	if verifyErr == nil {
		t.Fatal("expected error for RS256 token, got nil")
	}
}
