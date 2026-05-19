package staging_test

import (
	"encoding/json"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/nqhhdev/ivelox-core/internal/domain"
)

func TestPendingExam_JSONSerializable(t *testing.T) {
	pe := &domain.PendingExam{
		ID:         uuid.New(),
		SourceURL:  "https://example.com/test1",
		SourceName: "kmf",
		Series:     "Cambridge 18",
		TestNumber: 1,
		Year:       2023,
		RawData:    map[string]any{"reading": map[string]any{"sections": []string{"passage1"}}},
		AINormalized: map[string]any{"quality_score": 8.5},
		HasReading: true,
		Status:     "pending",
		ScrapedAt:  time.Now(),
	}

	rawJSON, err := json.Marshal(pe.RawData)
	if err != nil {
		t.Fatalf("RawData not serializable: %v", err)
	}
	if len(rawJSON) == 0 {
		t.Fatal("RawData serialized to empty")
	}

	aiJSON, err := json.Marshal(pe.AINormalized)
	if err != nil {
		t.Fatalf("AINormalized not serializable: %v", err)
	}
	if len(aiJSON) == 0 {
		t.Fatal("AINormalized serialized to empty")
	}
}
