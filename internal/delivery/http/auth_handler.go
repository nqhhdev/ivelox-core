package http

import (
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/nqhhdev/ivelox-core/internal/usecase"
)

// authError maps known error messages to HTTP status codes.
func authError(c *gin.Context, err error) {
	msg := err.Error()
	switch {
	case strings.Contains(msg, "too many requests"):
		c.JSON(http.StatusTooManyRequests, gin.H{"error": msg})
	case strings.Contains(strings.ToLower(msg), "invalid credentials"),
		strings.Contains(strings.ToLower(msg), "invalid login"),
		strings.Contains(strings.ToLower(msg), "invalid refresh"),
		strings.Contains(strings.ToLower(msg), "email not confirmed"):
		c.JSON(http.StatusUnauthorized, gin.H{"error": msg})
	default:
		c.JSON(http.StatusBadRequest, gin.H{"error": msg})
	}
}

type AuthHandler struct {
	authUC *usecase.AuthUsecase
}

func NewAuthHandler(authUC *usecase.AuthUsecase) *AuthHandler {
	return &AuthHandler{authUC: authUC}
}

// Verify godoc
//
//	@Summary		Verify JWT and sync profile
//	@Description	Validates Supabase JWT, upserts user profile (works for email + Google OAuth), returns profile
//	@Tags			auth
//	@Produce		json
//	@Security		BearerAuth
//	@Success		200	{object}	ProfileResponse
//	@Failure		401	{object}	ErrorResponse
//	@Failure		500	{object}	ErrorResponse
//	@Router			/auth/verify [post]
func (h *AuthHandler) Verify(c *gin.Context) {
	user, err := h.authUC.UpsertFromJWT(
		c.GetString("userID"),
		c.GetString("userEmail"),
		c.GetString("userProvider"),
		c.GetString("userAvatarURL"),
		c.GetString("userDisplayName"),
	)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to sync profile"})
		return
	}
	c.JSON(http.StatusOK, ProfileResponse{
		ID:          user.ID.String(),
		Email:       user.Email,
		DisplayName: user.DisplayName,
		AvatarURL:   user.AvatarURL,
		Provider:    user.Provider,
		Role:        user.Role,
	})
}

// Register godoc
//
//	@Summary		Register a new user
//	@Description	Creates a new account via Supabase Auth (email/password). Password must be at least 8 characters and contain uppercase, lowercase, number, and special character. Returns needs_verification=true — user must confirm email before logging in.
//	@Tags			auth
//	@Accept			json
//	@Produce		json
//	@Param			body	body		RegisterRequest	true	"Register payload"
//	@Success		201		{object}	TokenResponse
//	@Failure		400		{object}	ErrorResponse
//	@Router			/auth/register [post]
func (h *AuthHandler) Register(c *gin.Context) {
	var req RegisterRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	result, err := h.authUC.Register(req.Email, req.Password)
	if err != nil {
		authError(c, err)
		return
	}

	c.JSON(http.StatusCreated, TokenResponse{
		AccessToken:       result.AccessToken,
		RefreshToken:      result.RefreshToken,
		UserID:            result.UserID,
		Email:             result.Email,
		NeedsVerification: result.NeedsVerification,
	})
}

// Login godoc
//
//	@Summary		Login with email and password
//	@Description	Authenticates via Supabase Auth. Google OAuth users get JWT from Supabase JS SDK directly — no login endpoint needed.
//	@Tags			auth
//	@Accept			json
//	@Produce		json
//	@Param			body	body		LoginRequest	true	"Login payload"
//	@Success		200		{object}	TokenResponse
//	@Failure		400		{object}	ErrorResponse
//	@Failure		401		{object}	ErrorResponse
//	@Router			/auth/login [post]
func (h *AuthHandler) Login(c *gin.Context) {
	var req LoginRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	result, err := h.authUC.Login(req.Email, req.Password)
	if err != nil {
		authError(c, err)
		return
	}

	c.JSON(http.StatusOK, TokenResponse{
		AccessToken:  result.AccessToken,
		RefreshToken: result.RefreshToken,
		UserID:       result.UserID,
		Email:        result.Email,
	})
}

// Refresh godoc
//
//	@Summary		Refresh access token
//	@Description	Exchanges a refresh token for a new access token + refresh token pair.
//	@Tags			auth
//	@Accept			json
//	@Produce		json
//	@Param			body	body		RefreshRequest	true	"Refresh payload"
//	@Success		200		{object}	TokenResponse
//	@Failure		400		{object}	ErrorResponse
//	@Failure		401		{object}	ErrorResponse
//	@Router			/auth/refresh [post]
func (h *AuthHandler) Refresh(c *gin.Context) {
	var req RefreshRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request body"})
		return
	}

	result, err := h.authUC.Refresh(req.RefreshToken)
	if err != nil {
		authError(c, err)
		return
	}

	c.JSON(http.StatusOK, TokenResponse{
		AccessToken:  result.AccessToken,
		RefreshToken: result.RefreshToken,
		UserID:       result.UserID,
		Email:        result.Email,
	})
}

// Logout godoc
//
//	@Summary		Logout current user
//	@Description	Revokes the current session token via Supabase. Client should discard tokens after this call.
//	@Tags			auth
//	@Produce		json
//	@Security		BearerAuth
//	@Success		204
//	@Failure		500	{object}	ErrorResponse
//	@Router			/auth/logout [post]
func (h *AuthHandler) Logout(c *gin.Context) {
	// Extract raw token from Authorization header to pass to Supabase for revocation.
	token := c.GetHeader("Authorization")
	if len(token) > 7 {
		token = token[7:] // trim "Bearer "
	}

	if err := h.authUC.Logout(token); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "logout failed"})
		return
	}

	c.Status(http.StatusNoContent)
}

// Request / Response types

type RegisterRequest struct {
	Email    string `json:"email"    binding:"required,email" example:"user@example.com"`
	Password string `json:"password" binding:"required,min=8" example:"Secret123!"`
}

type LoginRequest struct {
	Email    string `json:"email"    binding:"required,email" example:"user@example.com"`
	Password string `json:"password" binding:"required"        example:"secret123"`
}

type RefreshRequest struct {
	RefreshToken string `json:"refresh_token" binding:"required" example:"eyJhbGci..."`
}

type TokenResponse struct {
	AccessToken       string `json:"access_token"        example:"eyJhbGci..."`
	RefreshToken      string `json:"refresh_token"       example:"eyJhbGci..."`
	UserID            string `json:"user_id"             example:"550e8400-e29b-41d4-a716-446655440000"`
	Email             string `json:"email"               example:"user@example.com"`
	NeedsVerification bool   `json:"needs_verification"  example:"true"`
}

type ProfileResponse struct {
	ID          string `json:"id"           example:"550e8400-e29b-41d4-a716-446655440000"`
	Email       string `json:"email"        example:"user@example.com"`
	DisplayName string `json:"display_name" example:"John Doe"`
	AvatarURL   string `json:"avatar_url"   example:"https://lh3.googleusercontent.com/..."`
	Provider    string `json:"provider"     example:"google"`
	Role        string `json:"role"         example:"user"`
}

type ErrorResponse struct {
	Error string `json:"error" example:"invalid request body"`
}
