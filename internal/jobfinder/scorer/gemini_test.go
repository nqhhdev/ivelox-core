package scorer_test

import (
	"testing"

	"github.com/nqhhdev/ivelox-core/internal/jobfinder/scorer"
)

func TestExtractJSON_RequiresAPIKey(t *testing.T) {
	t.Skip("integration: requires GEMINI_API_KEY")
}

func TestScoredJob_ScoreThreshold(t *testing.T) {
	sj := scorer.ScoredJob{
		Score:        75,
		MatchReasons: []string{"6yr Flutter"},
		WorkType:     "remote",
		Seniority:    "senior",
	}
	if sj.Score != 75 {
		t.Fatalf("expected 75, got %d", sj.Score)
	}
	if sj.WorkType != "remote" {
		t.Fatalf("expected remote, got %s", sj.WorkType)
	}
}
