package supabase

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"

	"github.com/nqhhdev/ivelox-core/internal/domain"
)

// AuthClient wraps the Supabase Auth REST API.
type AuthClient struct {
	baseURL string
	anonKey string
	client  *http.Client
}

func NewAuthClient(supabaseURL, anonKey string) *AuthClient {
	return &AuthClient{
		baseURL: supabaseURL,
		anonKey: anonKey,
		client:  &http.Client{},
	}
}

type SignUpRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

type SignInRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

type refreshRequest struct {
	RefreshToken string `json:"refresh_token"`
}

type authResponse struct {
	AccessToken  string   `json:"access_token"`
	RefreshToken string   `json:"refresh_token"`
	User         authUser `json:"user"`
	// Supabase signup with email confirmation enabled returns id/email at root level
	ID    string `json:"id"`
	Email string `json:"email"`
}

type authUser struct {
	ID    string `json:"id"`
	Email string `json:"email"`
}

type authError struct {
	Message string `json:"message"`
	Code    string `json:"error_code"`
}

// SignUp implements domain.AuthProvider.
func (c *AuthClient) SignUp(email, password string) (*domain.AuthResult, error) {
	r, err := c.post("/auth/v1/signup", SignUpRequest{Email: email, Password: password})
	if err != nil {
		return nil, err
	}
	// When email confirmation is enabled, Supabase returns id/email at root level
	// and access_token is empty. When disabled, they are inside user{}.
	userID := r.User.ID
	if userID == "" {
		userID = r.ID
	}
	userEmail := r.User.Email
	if userEmail == "" {
		userEmail = r.Email
	}
	return &domain.AuthResult{
		AccessToken:       r.AccessToken,
		RefreshToken:      r.RefreshToken,
		UserID:            userID,
		Email:             userEmail,
		NeedsVerification: r.AccessToken == "",
	}, nil
}

// SignIn implements domain.AuthProvider.
func (c *AuthClient) SignIn(email, password string) (*domain.AuthResult, error) {
	r, err := c.post("/auth/v1/token?grant_type=password", SignInRequest{Email: email, Password: password})
	if err != nil {
		return nil, err
	}
	return &domain.AuthResult{
		AccessToken:  r.AccessToken,
		RefreshToken: r.RefreshToken,
		UserID:       r.User.ID,
		Email:        r.User.Email,
	}, nil
}

// RefreshToken implements domain.AuthProvider.
func (c *AuthClient) RefreshToken(refreshToken string) (*domain.AuthResult, error) {
	r, err := c.post("/auth/v1/token?grant_type=refresh_token", refreshRequest{RefreshToken: refreshToken})
	if err != nil {
		return nil, err
	}
	return &domain.AuthResult{
		AccessToken:  r.AccessToken,
		RefreshToken: r.RefreshToken,
		UserID:       r.User.ID,
		Email:        r.User.Email,
	}, nil
}

// SignOut implements domain.AuthProvider.
func (c *AuthClient) SignOut(accessToken string) error {
	req, err := http.NewRequest(http.MethodPost, c.baseURL+"/auth/v1/logout", nil)
	if err != nil {
		return err
	}
	req.Header.Set("apikey", c.anonKey)
	req.Header.Set("Authorization", "Bearer "+accessToken)

	resp, err := c.client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		return fmt.Errorf("supabase logout error %d", resp.StatusCode)
	}
	return nil
}

func (c *AuthClient) post(path string, body any) (*authResponse, error) {
	b, err := json.Marshal(body)
	if err != nil {
		return nil, err
	}

	req, err := http.NewRequest(http.MethodPost, c.baseURL+path, bytes.NewReader(b))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("apikey", c.anonKey)

	resp, err := c.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	if resp.StatusCode >= 400 {
		var e authError
		_ = json.Unmarshal(raw, &e)
		if e.Message != "" {
			return nil, fmt.Errorf("%s", e.Message)
		}
		switch resp.StatusCode {
		case http.StatusTooManyRequests:
			return nil, fmt.Errorf("Too many requests, please try again later")
		case http.StatusUnauthorized:
			return nil, fmt.Errorf("Invalid credentials")
		case http.StatusUnprocessableEntity:
			return nil, fmt.Errorf("Invalid request data")
		default:
			return nil, fmt.Errorf("Authentication error (%d)", resp.StatusCode)
		}
	}

	var result authResponse
	if err := json.Unmarshal(raw, &result); err != nil {
		return nil, err
	}
	return &result, nil
}
