package telegram_test

import (
	"testing"

	"github.com/nqhhdev/ivelox-core/internal/telegram"
)

func TestNewBot_InvalidToken(t *testing.T) {
	_, err := telegram.NewBot("invalid-token", 0, nil)
	if err == nil {
		t.Fatal("expected error for invalid token")
	}
}
