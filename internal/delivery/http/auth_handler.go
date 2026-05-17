package http

import (
	"github.com/gin-gonic/gin"
	"github.com/nqhhdev/ivelox-core/internal/usecase"
)

type AuthHandler struct {
	authUC *usecase.AuthUsecase
}

func NewAuthHandler(authUC *usecase.AuthUsecase) *AuthHandler {
	return &AuthHandler{authUC: authUC}
}

func (h *AuthHandler) Verify(c *gin.Context) {
	userID := c.GetString("userID")
	user, err := h.authUC.GetProfile(userID)
	if err != nil {
		c.JSON(404, gin.H{"error": "profile not found"})
		return
	}
	c.JSON(200, gin.H{
		"id":           user.ID,
		"display_name": user.DisplayName,
		"role":         user.Role,
	})
}
