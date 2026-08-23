package notifier_test

import (
	"strings"
	"testing"

	"github.com/nqhhdev/ivelox-core/internal/jobfinder/fetcher"
	"github.com/nqhhdev/ivelox-core/internal/jobfinder/notifier"
	"github.com/nqhhdev/ivelox-core/internal/jobfinder/scorer"
)

func TestFormatJobMessage_Green(t *testing.T) {
	job := scorer.ScoredJob{
		RawJob: fetcher.RawJob{
			Title:    "Senior Flutter Developer",
			Company:  "Grab",
			Location: "Remote (SEA)",
			Salary:   "$3,000 – $5,000/month",
			Source:   "remotive",
		},
		Score:        87,
		MatchReasons: []string{"6yr Flutter — senior requirement met", "Clean Architecture experience"},
		GapSkills:    []string{"Kotlin (nice-to-have)"},
		WorkType:     "remote",
	}

	msg := notifier.FormatJobMessage(job)

	checks := []string{"🟢", "87/100", "Senior Flutter Developer", "Grab", "remotive", "Why you match", "Skill gaps"}
	for _, c := range checks {
		if !strings.Contains(msg, c) {
			t.Errorf("message missing %q\nGot:\n%s", c, msg)
		}
	}
}

func TestFormatJobMessage_Yellow(t *testing.T) {
	job := scorer.ScoredJob{
		RawJob:   fetcher.RawJob{Title: "Mobile Dev", Company: "Startup", Source: "arbeitnow"},
		Score:    65,
		WorkType: "hybrid",
	}
	msg := notifier.FormatJobMessage(job)
	if !strings.Contains(msg, "🟡") {
		t.Errorf("expected yellow badge for score 65\nGot: %s", msg)
	}
}
