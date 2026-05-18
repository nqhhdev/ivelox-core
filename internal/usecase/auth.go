package usecase

import (
	"fmt"
	"regexp"
	"unicode"

	"github.com/google/uuid"
	"github.com/nqhhdev/ivelox-core/internal/domain"
)

var emailRe = regexp.MustCompile(`^[^@\s]+@[^@\s]+\.[^@\s]+$`)

// validatePassword enforces password strength:
//   - At least 8 characters
//   - At least 1 uppercase letter (A-Z)
//   - At least 1 lowercase letter (a-z)
//   - At least 1 digit (0-9)
//   - At least 1 special character (!@#$%^&* etc.)
func validatePassword(p string) error {
	if len(p) < 8 {
		return fmt.Errorf("password must be at least 8 characters")
	}
	var hasUpper, hasLower, hasDigit, hasSpecial bool
	for _, c := range p {
		switch {
		case unicode.IsUpper(c):
			hasUpper = true
		case unicode.IsLower(c):
			hasLower = true
		case unicode.IsDigit(c):
			hasDigit = true
		case unicode.IsPunct(c) || unicode.IsSymbol(c):
			hasSpecial = true
		}
	}
	if !hasUpper {
		return fmt.Errorf("password must contain at least one uppercase letter")
	}
	if !hasLower {
		return fmt.Errorf("password must contain at least one lowercase letter")
	}
	if !hasDigit {
		return fmt.Errorf("password must contain at least one number")
	}
	if !hasSpecial {
		return fmt.Errorf("password must contain at least one special character")
	}
	return nil
}

type AuthUsecase struct {
	userRepo     domain.UserRepository
	authProvider domain.AuthProvider
}

func NewAuthUsecase(userRepo domain.UserRepository, authProvider domain.AuthProvider) *AuthUsecase {
	return &AuthUsecase{userRepo: userRepo, authProvider: authProvider}
}

func (u *AuthUsecase) GetProfile(userIDStr string) (*domain.User, error) {
	id, err := uuid.Parse(userIDStr)
	if err != nil {
		return nil, fmt.Errorf("invalid user id: %w", err)
	}
	return u.userRepo.GetByID(id)
}

func (u *AuthUsecase) Register(email, password string) (*domain.AuthResult, error) {
	if err := validatePassword(password); err != nil {
		return nil, err
	}
	result, err := u.authProvider.SignUp(email, password)
	if err != nil {
		return nil, err
	}
	userID, err := uuid.Parse(result.UserID)
	if err != nil {
		return nil, fmt.Errorf("invalid user id from auth provider: %w", err)
	}
	// Skip profile upsert when email confirmation is required —
	// profile will be created on first /auth/verify after confirmation.
	if !result.NeedsVerification {
		profile := &domain.User{
			ID:       userID,
			Email:    result.Email,
			Provider: "email",
		}
		if err := u.userRepo.Upsert(profile); err != nil {
			return nil, fmt.Errorf("upsert profile: %w", err)
		}
	}
	return result, nil
}

func (u *AuthUsecase) Login(email, password string) (*domain.AuthResult, error) {
	result, err := u.authProvider.SignIn(email, password)
	if err != nil {
		return nil, err
	}
	return result, nil
}

func (u *AuthUsecase) Refresh(refreshToken string) (*domain.AuthResult, error) {
	return u.authProvider.RefreshToken(refreshToken)
}

func (u *AuthUsecase) Logout(accessToken string) error {
	return u.authProvider.SignOut(accessToken)
}

// UpsertFromJWT upserts a user profile from JWT claims.
// Called on every /auth/verify to keep profile in sync (especially for Google OAuth users).
func (u *AuthUsecase) UpsertFromJWT(userIDStr, email, provider, avatarURL, displayName string) (*domain.User, error) {
	id, err := uuid.Parse(userIDStr)
	if err != nil {
		return nil, fmt.Errorf("invalid user id: %w", err)
	}

	// Try to load existing profile first to preserve fields like onboarding_step.
	existing, err := u.userRepo.GetByID(id)
	if err != nil {
		// Profile doesn't exist yet (first Google login) — create fresh.
		existing = &domain.User{
			ID:       id,
			Email:    email,
			Provider: provider,
			AvatarURL: avatarURL,
			DisplayName: displayName,
		}
	} else {
		// Update mutable fields from JWT claims.
		existing.Email = email
		existing.Provider = provider
		if avatarURL != "" {
			existing.AvatarURL = avatarURL
		}
		// Only set display name from JWT if user hasn't set one manually yet.
		if existing.DisplayName == "" && displayName != "" {
			existing.DisplayName = displayName
		}
	}

	if err := u.userRepo.Upsert(existing); err != nil {
		return nil, fmt.Errorf("upsert profile: %w", err)
	}
	return existing, nil
}
