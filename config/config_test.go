package config_test

import (
	"testing"

	"github.com/nqhhdev/ivelox-core/config"
)

func TestLoad_WithEnvVars(t *testing.T) {
	t.Setenv("PORT", "9090")
	t.Setenv("FRONTEND_URL", "https://example.com")
	t.Setenv("SUPABASE_URL", "https://test.supabase.co")
	t.Setenv("SUPABASE_JWT_SECRET", "test-secret")
	t.Setenv("DATABASE_URL", "postgresql://localhost/test")

	cfg := config.Load()

	if cfg.Port != "9090" {
		t.Errorf("expected Port '9090', got %q", cfg.Port)
	}
	if cfg.FrontendURL != "https://example.com" {
		t.Errorf("expected FrontendURL 'https://example.com', got %q", cfg.FrontendURL)
	}
	if cfg.SupabaseURL != "https://test.supabase.co" {
		t.Errorf("expected SupabaseURL 'https://test.supabase.co', got %q", cfg.SupabaseURL)
	}
	if cfg.SupabaseJWTSecret != "test-secret" {
		t.Errorf("expected SupabaseJWTSecret 'test-secret', got %q", cfg.SupabaseJWTSecret)
	}
	if cfg.DatabaseURL != "postgresql://localhost/test" {
		t.Errorf("expected DatabaseURL 'postgresql://localhost/test', got %q", cfg.DatabaseURL)
	}
}

func TestLoad_DefaultPort(t *testing.T) {
	t.Setenv("PORT", "")
	t.Setenv("SUPABASE_URL", "https://test.supabase.co")
	t.Setenv("SUPABASE_JWT_SECRET", "test-secret")
	t.Setenv("DATABASE_URL", "postgresql://localhost/test")

	cfg := config.Load()

	if cfg.Port != "8080" {
		t.Errorf("expected default Port '8080', got %q", cfg.Port)
	}
}

func TestLoad_DefaultFrontendURL(t *testing.T) {
	t.Setenv("FRONTEND_URL", "")
	t.Setenv("SUPABASE_URL", "https://test.supabase.co")
	t.Setenv("SUPABASE_JWT_SECRET", "test-secret")
	t.Setenv("DATABASE_URL", "postgresql://localhost/test")

	cfg := config.Load()

	if cfg.FrontendURL != "http://localhost:5173" {
		t.Errorf("expected default FrontendURL 'http://localhost:5173', got %q", cfg.FrontendURL)
	}
}
