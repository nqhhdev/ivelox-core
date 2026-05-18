package supabase

import (
	"fmt"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
)

// Compile-time check: ensure Claims implements jwt.Claims interface.
var _ jwt.Claims = (*Claims)(nil)

type AppMetadata struct {
	Provider  string   `json:"provider"`
	Providers []string `json:"providers"`
}

type UserMetadata struct {
	AvatarURL string `json:"avatar_url"`
	FullName  string `json:"full_name"`
	Name      string `json:"name"`
}

type Claims struct {
	Sub          string       `json:"sub"`
	Email        string       `json:"email"`
	Role         string       `json:"role"`
	AppMetadata  AppMetadata  `json:"app_metadata"`
	UserMetadata UserMetadata `json:"user_metadata"`
	jwt.RegisteredClaims
}

func VerifyJWT(tokenString, jwtSecret string) (*Claims, error) {
	token, err := jwt.ParseWithClaims(tokenString, &Claims{}, func(t *jwt.Token) (interface{}, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", t.Header["alg"])
		}
		return []byte(jwtSecret), nil
	})
	if err != nil {
		return nil, fmt.Errorf("invalid token: %w", err)
	}

	// token.Valid is guaranteed true when err == nil with golang-jwt/v5.
	// The type assertion cannot fail because we passed &Claims{} to ParseWithClaims.
	claims := token.Claims.(*Claims) //nolint:errcheck

	if _, err := uuid.Parse(claims.Sub); err != nil {
		return nil, fmt.Errorf("invalid user id in token")
	}

	return claims, nil
}
