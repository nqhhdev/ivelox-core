package usecase_test

import (
	"fmt"
	"testing"

	"github.com/google/uuid"
	"github.com/nqhhdev/ivelox-core/internal/domain"
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
	f.users[u.ID] = u
	return nil
}

type fakeAuthProvider struct {
	signUpErr    error
	signInErr    error
	refreshErr   error
	signOutErr   error
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

func (f *fakeAuthProvider) RefreshToken(refreshToken string) (*domain.AuthResult, error) {
	if f.refreshErr != nil {
		return nil, f.refreshErr
	}
	return &domain.AuthResult{
		AccessToken:  "new-access-tok",
		RefreshToken: "new-refresh-tok",
		UserID:       "00000000-0000-0000-0000-000000000001",
		Email:        "user@example.com",
	}, nil
}

func (f *fakeAuthProvider) SignOut(accessToken string) error {
	return f.signOutErr
}

// --- GetProfile ---

func TestGetProfile_ReturnsUser(t *testing.T) {
	userID := uuid.New()
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{
		userID: {ID: userID, DisplayName: "Test User", Role: "user"},
	}}
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{})

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
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{})

	_, err := uc.GetProfile(uuid.New().String())
	if err == nil {
		t.Fatal("expected error, got nil")
	}
}

func TestGetProfile_InvalidUUID(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{})

	_, err := uc.GetProfile("not-a-uuid")
	if err == nil {
		t.Fatal("expected error for invalid UUID, got nil")
	}
}

// --- Register ---

func TestRegister_Success(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{})

	result, err := uc.Register("user@example.com", "password123")
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if result.AccessToken == "" {
		t.Error("expected access token, got empty")
	}
	if result.Email != "user@example.com" {
		t.Errorf("expected email 'user@example.com', got %q", result.Email)
	}

	// profile must be upserted into repo
	id, _ := uuid.Parse(result.UserID)
	saved, err := repo.GetByID(id)
	if err != nil {
		t.Fatalf("profile not saved in repo: %v", err)
	}
	if saved.Email != "user@example.com" {
		t.Errorf("expected saved email 'user@example.com', got %q", saved.Email)
	}
	if saved.Provider != "email" {
		t.Errorf("expected provider 'email', got %q", saved.Provider)
	}
}

func TestRegister_AuthProviderError(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{
		signUpErr: fmt.Errorf("email already registered"),
	})

	_, err := uc.Register("user@example.com", "password123")
	if err == nil {
		t.Fatal("expected error, got nil")
	}
}

// --- Login ---

func TestLogin_Success(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{})

	result, err := uc.Login("user@example.com", "password123")
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if result.AccessToken == "" {
		t.Error("expected access token, got empty")
	}
}

func TestLogin_AuthProviderError(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{
		signInErr: fmt.Errorf("invalid login credentials"),
	})

	_, err := uc.Login("user@example.com", "wrongpassword")
	if err == nil {
		t.Fatal("expected error, got nil")
	}
}

// --- Refresh ---

func TestRefresh_Success(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{})

	result, err := uc.Refresh("old-refresh-tok")
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if result.AccessToken != "new-access-tok" {
		t.Errorf("expected new access token, got %q", result.AccessToken)
	}
	if result.RefreshToken != "new-refresh-tok" {
		t.Errorf("expected new refresh token, got %q", result.RefreshToken)
	}
}

func TestRefresh_InvalidToken(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{
		refreshErr: fmt.Errorf("invalid refresh token"),
	})

	_, err := uc.Refresh("bad-token")
	if err == nil {
		t.Fatal("expected error, got nil")
	}
}

// --- Logout ---

func TestLogout_Success(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{})

	if err := uc.Logout("access-tok"); err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
}

func TestLogout_Error(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{
		signOutErr: fmt.Errorf("logout failed"),
	})

	if err := uc.Logout("access-tok"); err == nil {
		t.Fatal("expected error, got nil")
	}
}
