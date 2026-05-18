package http

import (
	"github.com/gin-gonic/gin"
	swaggerFiles "github.com/swaggo/files"
	ginSwagger "github.com/swaggo/gin-swagger"
	"github.com/nqhhdev/ivelox-core/internal/middleware"
	"github.com/nqhhdev/ivelox-core/internal/usecase"

	_ "github.com/nqhhdev/ivelox-core/docs" // swagger generated docs
)

func NewRouter(frontendURL, jwtSecret string, authUC *usecase.AuthUsecase) *gin.Engine {
	r := gin.Default()
	r.Use(middleware.CORS(frontendURL))

	// Swagger UI
	r.GET("/swagger/*any", ginSwagger.WrapHandler(swaggerFiles.Handler))

	authHandler := NewAuthHandler(authUC)

	api := r.Group("/api/v1")
	{
		api.GET("/health", func(c *gin.Context) {
			c.JSON(200, gin.H{"status": "ok"})
		})

		// Public auth routes
		api.POST("/auth/register", authHandler.Register)
		api.POST("/auth/login", authHandler.Login)

		// Protected routes
		protected := api.Group("")
		protected.Use(middleware.Auth(jwtSecret))
		{
			protected.POST("/auth/verify", authHandler.Verify)
		}
	}

	return r
}
