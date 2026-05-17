package usecase

import (
	"fmt"

	"github.com/google/uuid"
	"github.com/nqhhdev/ivelox-core/internal/domain"
)

type AuthUsecase struct {
	userRepo domain.UserRepository
}

func NewAuthUsecase(userRepo domain.UserRepository) *AuthUsecase {
	return &AuthUsecase{userRepo: userRepo}
}

func (u *AuthUsecase) GetProfile(userIDStr string) (*domain.User, error) {
	id, err := uuid.Parse(userIDStr)
	if err != nil {
		return nil, fmt.Errorf("invalid user id: %w", err)
	}
	return u.userRepo.GetByID(id)
}
