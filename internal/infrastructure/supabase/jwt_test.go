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
