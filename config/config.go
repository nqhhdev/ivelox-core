package config

import (
	"log"
	"os"

	"github.com/joho/godotenv"
)

type Config struct {
	Port              string
	FrontendURL       string
	SupabaseURL       string
	SupabaseJWTSecret string
	DatabaseURL       string
}

func Load() *Config {
	if err := godotenv.Load(); err != nil {
		log.Println("No .env file found, reading from environment")
	}
	return &Config{
		Port:              getEnv("PORT", "8080"),
		FrontendURL:       getEnv("FRONTEND_URL", "http://localhost:5173"),
		SupabaseURL:       mustGetEnv("SUPABASE_URL"),
		SupabaseJWTSecret: mustGetEnv("SUPABASE_JWT_SECRET"),
		DatabaseURL:       mustGetEnv("DATABASE_URL"),
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func mustGetEnv(key string) string {
	v := os.Getenv(key)
	if v == "" {
		log.Fatalf("required env var %s is not set", key)
	}
	return v
}
