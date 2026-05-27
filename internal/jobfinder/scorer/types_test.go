package scorer_test

import (
	"testing"

	"github.com/nqhhdev/ivelox-core/internal/jobfinder/fetcher"
	"github.com/nqhhdev/ivelox-core/internal/jobfinder/scorer"
)

func TestScoredJob_EmbedRawJob(t *testing.T) {
	sj := scorer.ScoredJob{
		RawJob: fetcher.RawJob{
			Title:  "Flutter Lead",
			Source: "arbeitnow",
		},
		Score:    85,
		WorkType: "remote",
	}
	if sj.Title != "Flutter Lead" {
		t.Fatalf("unexpected title: %s", sj.Title)
	}
	if sj.Score != 85 {
		t.Fatalf("unexpected score: %d", sj.Score)
	}
}
