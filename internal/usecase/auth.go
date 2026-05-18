package usecase

import (
	"fmt"

	"github.com/google/uuid"
	"github.com/nqhhdev/ivelox-core/internal/domain"
)

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
	result, err := u.authProvider.SignUp(email, password)
	if err != nil {
		return nil, err
	}
	userID, err := uuid.Parse(result.UserID)
	if err != nil {
		return nil, fmt.Errorf("invalid user id from auth provider: %w", err)
	}
	profile := &domain.User{
		ID:       userID,
		Email:    result.Email,
		Provider: "email",
	}
	if err := u.userRepo.Upsert(profile); err != nil {
		return nil, fmt.Errorf("upsert profile: %w", err)
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
