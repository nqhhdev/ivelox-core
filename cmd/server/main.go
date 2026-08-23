// @title           iVelox API
// @version         1.0
// @description     Personal backend platform API
// @host            localhost:8080
// @BasePath        /api/v1
// @securityDefinitions.apikey BearerAuth
// @in header
// @name Authorization
// @description Type "Bearer " followed by your Supabase JWT token
package main

import (
	"context"
	"fmt"
	"log"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/nqhhdev/ivelox-core/config"
	httpdelivery "github.com/nqhhdev/ivelox-core/internal/delivery/http"
	"github.com/nqhhdev/ivelox-core/internal/domain"
	"github.com/nqhhdev/ivelox-core/internal/infrastructure/gemini"
	"github.com/nqhhdev/ivelox-core/internal/infrastructure/supabase"
	"github.com/nqhhdev/ivelox-core/internal/repository/postgres"
	"github.com/nqhhdev/ivelox-core/internal/usecase"
)

func main() {
	cfg := config.Load()

	db, err := pgxpool.New(context.Background(), cfg.DatabaseURL)
	if err != nil {
		log.Fatalf("failed to connect to database: %v", err)
	}
	defer db.Close()

	userRepo := postgres.NewUserRepository(db)
	authClient := supabase.NewAuthClient(cfg.SupabaseURL, cfg.SupabaseAnonKey)
	authUC := usecase.NewAuthUsecase(userRepo, authClient)

	foodRepo := postgres.NewFoodCacheRepository(db)
	mealRepo := postgres.NewMealLogRepository(db)

	var nutrition domain.NutritionResolver
	if cfg.GeminiAPIKey == "" {
		log.Println("warning: GEMINI_API_KEY is empty; food resolve that needs AI will fail at runtime")
	} else {
		client, err := gemini.NewNutritionClient(context.Background(), cfg.GeminiAPIKey)
		if err != nil {
			log.Fatalf("failed to create gemini nutrition client: %v", err)
		}
		defer client.Close()
		nutrition = client
	}

	foodUC := usecase.NewFoodResolveUsecase(foodRepo, nutrition)
	mealUC := usecase.NewMealUsecase(mealRepo)

	router := httpdelivery.NewRouter(cfg.FrontendURL, cfg.SupabaseJWTSecret, authUC, foodUC, mealUC)

	addr := fmt.Sprintf(":%s", cfg.Port)
	log.Printf("Server starting on %s", addr)
	if err := router.Run(addr); err != nil {
		log.Fatalf("Failed to start server: %v", err)
	}
}
