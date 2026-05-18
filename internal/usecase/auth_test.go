package usecase_test

import (
	"fmt"
	"testing"

	"github.com/google/uuid"
	"github.com/nqhhdev/ivelox-core/internal/domain"
	"github.com/nqhhdev/ivelox-core/internal/usecase"
)

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

func TestGetProfile_ReturnsUser(t *testing.T) {
	userID := uuid.New()
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{
		userID: {ID: userID, DisplayName: "Test User", Role: "user"},
	}}
	uc := usecase.NewAuthUsecase(repo)

	user, err := uc.GetProfile(userID.String())
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if user.DisplayName != "Test User" {
		t.Errorf("expected 'Test User', got %q", user.DisplayName)
	}
}

func TestGetProfile_UserNotFound(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	uc := usecase.NewAuthUsecase(repo)

	_, err := uc.GetProfile(uuid.New().String())
	if err == nil {
		t.Fatal("expected error, got nil")
	}
}

func TestGetProfile_InvalidUUID(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	uc := usecase.NewAuthUsecase(repo)

	_, err := uc.GetProfile("not-a-uuid")
	if err == nil {
		t.Fatal("expected error for invalid UUID, got nil")
	}
}
