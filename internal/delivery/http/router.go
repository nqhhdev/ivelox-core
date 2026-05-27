package http

import (
	"github.com/gin-gonic/gin"
	"github.com/nqhhdev/ivelox-core/internal/middleware"
	"github.com/nqhhdev/ivelox-core/internal/usecase"
)

func NewRouter(frontendURL, jwtSecret string, authUC *usecase.AuthUsecase) *gin.Engine {
	r := gin.Default()
	r.Use(middleware.CORS(frontendURL))

	authHandler := NewAuthHandler(authUC)

	api := r.Group("/api/v1")
	{
		api.GET("/health", func(c *gin.Context) {
			c.JSON(200, gin.H{"status": "ok"})
		})

		// Public auth routes
		api.POST("/auth/register", authHandler.Register)
		api.POST("/auth/login", authHandler.Login)
		api.POST("/auth/refresh", authHandler.Refresh)

		// Protected routes
		protected := api.Group("")
		protected.Use(middleware.Auth(jwtSecret))
		{
			protected.POST("/auth/verify", authHandler.Verify)
			protected.POST("/auth/logout", authHandler.Logout)
		}
	}

	return r
}
