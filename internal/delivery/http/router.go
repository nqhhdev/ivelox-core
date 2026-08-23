package http

import (
	"github.com/gin-gonic/gin"
	"github.com/nqhhdev/ivelox-core/internal/middleware"
	"github.com/nqhhdev/ivelox-core/internal/usecase"
)

func NewRouter(frontendURL, jwtSecret string, authUC *usecase.AuthUsecase, foodUC *usecase.FoodResolveUsecase, mealUC *usecase.MealUsecase) *gin.Engine {
	r := gin.Default()
	r.Use(middleware.CORS(frontendURL))

	authHandler := NewAuthHandler(authUC)
	healthHandler := NewHealthHandler(foodUC, mealUC)

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

			protected.POST("/health/foods/resolve", healthHandler.ResolveFood)
			protected.POST("/health/meals", healthHandler.CreateMeal)
			protected.GET("/health/meals", healthHandler.ListMeals)
			protected.DELETE("/health/meals/:id", healthHandler.DeleteMeal)
			protected.GET("/health/check/today", healthHandler.TodayCheck)
		}
	}

	return r
}
