package config_test

import (
	"os"
	"testing"

	"github.com/nqhhdev/ivelox-core/config"
)

func TestLoad_ScraperFields(t *testing.T) {
	os.Setenv("PORT", "8080")
	os.Setenv("FRONTEND_URL", "http://localhost:5173")
	os.Setenv("SUPABASE_URL", "https://example.supabase.co")
	os.Setenv("SUPABASE_ANON_KEY", "anon")
	os.Setenv("SUPABASE_JWT_SECRET", "secret")
	os.Setenv("DATABASE_URL", "postgresql://localhost/test")
	os.Setenv("TELEGRAM_TOKEN", "123:abc")
	os.Setenv("TELEGRAM_CHAT_ID", "456789")
	os.Setenv("OPENAI_API_KEY", "sk-test")
	os.Setenv("SCRAPER_INTERVAL_HOURS", "8")

	cfg := config.Load()

	if cfg.TelegramToken != "123:abc" {
		t.Errorf("expected TelegramToken=123:abc got %s", cfg.TelegramToken)
	}
	if cfg.TelegramChatID != int64(456789) {
		t.Errorf("expected TelegramChatID=456789 got %d", cfg.TelegramChatID)
	}
	if cfg.OpenAIKey != "sk-test" {
		t.Errorf("expected OpenAIKey=sk-test got %s", cfg.OpenAIKey)
	}
	if cfg.ScraperIntervalHours != 8 {
		t.Errorf("expected ScraperIntervalHours=8 got %d", cfg.ScraperIntervalHours)
	}
}
