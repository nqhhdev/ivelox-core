package config

import (
	"log"
	"os"
	"strconv"

	"github.com/joho/godotenv"
)

type Config struct {
	Port                 string
	FrontendURL          string
	SupabaseURL          string
	SupabaseAnonKey      string
	SupabaseJWTSecret    string
	DatabaseURL          string
	TelegramToken        string
	TelegramChatID       int64
	OpenAIKey            string
	ScraperIntervalHours int
}

func Load() *Config {
	if err := godotenv.Load(); err != nil {
		log.Println("No .env file found, reading from environment")
	}
	chatID, _ := strconv.ParseInt(getEnv("TELEGRAM_CHAT_ID", "0"), 10, 64)
	interval, _ := strconv.Atoi(getEnv("SCRAPER_INTERVAL_HOURS", "8"))
	return &Config{
		Port:                 getEnv("PORT", "8080"),
		FrontendURL:          getEnv("FRONTEND_URL", "http://localhost:5173"),
		SupabaseURL:          mustGetEnv("SUPABASE_URL"),
		SupabaseAnonKey:      mustGetEnv("SUPABASE_ANON_KEY"),
		SupabaseJWTSecret:    mustGetEnv("SUPABASE_JWT_SECRET"),
		DatabaseURL:          mustGetEnv("DATABASE_URL"),
		TelegramToken:        getEnv("TELEGRAM_TOKEN", ""),
		TelegramChatID:       chatID,
		OpenAIKey:            getEnv("OPENAI_API_KEY", ""),
		ScraperIntervalHours: interval,
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
