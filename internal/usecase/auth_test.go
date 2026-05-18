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
	users     map[uuid.UUID]*domain.User
	upsertErr error
}

func (f *fakeUserRepo) GetByID(id uuid.UUID) (*domain.User, error) {
	u, ok := f.users[id]
	if !ok {
		return nil, fmt.Errorf("not found")
	}
	return u, nil
}

func (f *fakeUserRepo) Upsert(u *domain.User) error {
	if f.upsertErr != nil {
		return f.upsertErr
	}
	if f.users == nil {
		f.users = map[uuid.UUID]*domain.User{}
	}
	f.users[u.ID] = u
	return nil
}

type fakeAuthProvider struct {
	signUpErr         error
	signInErr         error
	refreshErr        error
	signOutErr        error
	needsVerification bool
	invalidUserID     bool
}

func (f *fakeAuthProvider) SignUp(email, password string) (*domain.AuthResult, error) {
	if f.signUpErr != nil {
		return nil, f.signUpErr
	}
	accessToken := "access-tok"
	if f.needsVerification {
		accessToken = ""
	}
	userID := "00000000-0000-0000-0000-000000000001"
	if f.invalidUserID {
		userID = "not-a-valid-uuid"
	}
	return &domain.AuthResult{
		AccessToken:       accessToken,
		RefreshToken:      "refresh-tok",
		UserID:            userID,
		Email:             email,
		NeedsVerification: f.needsVerification,
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

// --- Register password validation ---

func TestRegister_WeakPasswords(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{})

	cases := []struct {
		name     string
		password string
	}{
		{"too short", "Ab1!"},
		{"no uppercase", "abc1234!"},
		{"no lowercase", "ABC1234!"},
		{"no digit", "Abcdefg!"},
		{"no special char", "Secret123"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			_, err := uc.Register("user@example.com", tc.password)
			if err == nil {
				t.Errorf("expected error for password %q, got nil", tc.password)
			}
		})
	}
}

func TestRegister_StrongPassword(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{})

	cases := []string{"Secret123!", "TestPass123!", "MyP4ssw0rd!", "Hello1World@"}
	for _, pw := range cases {
		t.Run(pw, func(t *testing.T) {
			_, err := uc.Register("user@example.com", pw)
			if err != nil {
				t.Errorf("expected no error for password %q, got %v", pw, err)
			}
		})
	}
}

// --- Register ---

func TestRegister_Success(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{})

	result, err := uc.Register("user@example.com", "Password123!")
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

	_, err := uc.Register("user@example.com", "Password123!")
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

// --- Register: NeedsVerification path ---

func TestRegister_NeedsVerification_SkipsProfileUpsert(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{needsVerification: true})

	result, err := uc.Register("user@example.com", "Password123!")
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if !result.NeedsVerification {
		t.Error("expected NeedsVerification=true")
	}
	// Profile must NOT be upserted when needs_verification=true
	if len(repo.users) != 0 {
		t.Errorf("expected empty repo, got %d users", len(repo.users))
	}
}

func TestRegister_NoVerification_UpsertsProfile(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{needsVerification: false})

	result, err := uc.Register("user@example.com", "Password123!")
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if result.NeedsVerification {
		t.Error("expected NeedsVerification=false")
	}
	if len(repo.users) != 1 {
		t.Errorf("expected 1 user in repo, got %d", len(repo.users))
	}
}

// --- UpsertFromJWT ---

func TestUpsertFromJWT_CreatesNewUser(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{})

	userID := uuid.New()
	user, err := uc.UpsertFromJWT(userID.String(), "google@example.com", "google", "https://avatar.url/photo.jpg", "Google User")
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if user.Email != "google@example.com" {
		t.Errorf("expected email 'google@example.com', got %q", user.Email)
	}
	if user.Provider != "google" {
		t.Errorf("expected provider 'google', got %q", user.Provider)
	}
	if user.AvatarURL != "https://avatar.url/photo.jpg" {
		t.Errorf("expected avatar_url set, got %q", user.AvatarURL)
	}
	if user.DisplayName != "Google User" {
		t.Errorf("expected display_name 'Google User', got %q", user.DisplayName)
	}
}

func TestUpsertFromJWT_UpdatesExistingUser(t *testing.T) {
	userID := uuid.New()
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{
		userID: {
			ID:          userID,
			Email:       "old@example.com",
			Provider:    "email",
			DisplayName: "Old Name",
			AvatarURL:   "",
		},
	}}
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{})

	user, err := uc.UpsertFromJWT(userID.String(), "new@example.com", "google", "https://avatar.url/photo.jpg", "New Name")
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	// Email and provider updated
	if user.Email != "new@example.com" {
		t.Errorf("expected updated email, got %q", user.Email)
	}
	if user.Provider != "google" {
		t.Errorf("expected updated provider, got %q", user.Provider)
	}
	// AvatarURL updated when non-empty
	if user.AvatarURL != "https://avatar.url/photo.jpg" {
		t.Errorf("expected avatar_url updated, got %q", user.AvatarURL)
	}
}

func TestUpsertFromJWT_PreservesManualDisplayName(t *testing.T) {
	userID := uuid.New()
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{
		userID: {
			ID:          userID,
			Email:       "user@example.com",
			DisplayName: "My Custom Name", // manually set by user
		},
	}}
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{})

	user, err := uc.UpsertFromJWT(userID.String(), "user@example.com", "google", "", "JWT Name")
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	// Manual display name must NOT be overwritten by JWT
	if user.DisplayName != "My Custom Name" {
		t.Errorf("expected preserved display_name 'My Custom Name', got %q", user.DisplayName)
	}
}

func TestUpsertFromJWT_SetsDisplayNameWhenEmpty(t *testing.T) {
	userID := uuid.New()
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{
		userID: {ID: userID, Email: "user@example.com", DisplayName: ""},
	}}
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{})

	user, err := uc.UpsertFromJWT(userID.String(), "user@example.com", "google", "", "From JWT")
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if user.DisplayName != "From JWT" {
		t.Errorf("expected display_name 'From JWT', got %q", user.DisplayName)
	}
}

func TestUpsertFromJWT_InvalidUUID(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{})

	_, err := uc.UpsertFromJWT("not-a-uuid", "user@example.com", "email", "", "")
	if err == nil {
		t.Fatal("expected error for invalid UUID, got nil")
	}
}

func TestUpsertFromJWT_UpsertError(t *testing.T) {
	repo := &fakeUserRepo{
		users:     map[uuid.UUID]*domain.User{},
		upsertErr: fmt.Errorf("db error"),
	}
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{})

	_, err := uc.UpsertFromJWT(uuid.New().String(), "user@example.com", "email", "", "")
	if err == nil {
		t.Fatal("expected error on upsert failure, got nil")
	}
}

func TestRegister_UpsertError(t *testing.T) {
	repo := &fakeUserRepo{
		users:     map[uuid.UUID]*domain.User{},
		upsertErr: fmt.Errorf("db error"),
	}
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{needsVerification: false})

	_, err := uc.Register("user@example.com", "Password123!")
	if err == nil {
		t.Fatal("expected error on upsert failure, got nil")
	}
}

func TestRegister_InvalidUserIDFromProvider(t *testing.T) {
	repo := &fakeUserRepo{users: map[uuid.UUID]*domain.User{}}
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{invalidUserID: true})

	_, err := uc.Register("user@example.com", "Password123!")
	if err == nil {
		t.Fatal("expected error for invalid user ID from provider, got nil")
	}
}
