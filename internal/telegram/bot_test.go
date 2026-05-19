package telegram_test

import (
	"testing"

	"github.com/google/uuid"
	"github.com/nqhhdev/ivelox-core/internal/domain"
	"github.com/nqhhdev/ivelox-core/internal/telegram"
)

func TestFormatPreviewMessage(t *testing.T) {
	pe := &domain.PendingExam{
		ID:           uuid.New(),
		SourceName:   "kmf",
		Series:       "Cambridge 18",
		TestNumber:   1,
		Year:         2022,
		QualityScore: 8.5,
		HasReading:   true,
		HasListening: true,
		HasWriting:   false,
		HasSpeaking:  false,
	}

	msg := telegram.FormatPreviewMessage(pe)

	checks := []string{"Cambridge 18", "8.5", "Reading", "Listening"}
	for _, s := range checks {
		found := false
		for i := 0; i <= len(msg)-len(s); i++ {
			if msg[i:i+len(s)] == s {
				found = true
				break
			}
		}
		if !found {
			t.Errorf("preview message missing %q\nGot: %s", s, msg)
		}
	}
}
