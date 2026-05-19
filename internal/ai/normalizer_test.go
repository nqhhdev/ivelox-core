package ai_test

import (
	"testing"

	"github.com/nqhhdev/ivelox-core/internal/ai"
	"github.com/nqhhdev/ivelox-core/internal/scraper"
)

func TestBuildPrompt_ContainsRequiredFields(t *testing.T) {
	raw := &scraper.RawExam{
		SourceURL:  "https://kmf.com/test",
		SourceName: "kmf",
		Series:     "Cambridge 18",
		TestNumber: 1,
		Year:       2022,
		Skills: map[string]*scraper.RawSkill{
			"reading": {
				Skill: "reading",
				Sections: []*scraper.RawSection{
					{Position: 1, Content: "Short passage.", Questions: []*scraper.RawQuestion{
						{Position: 1, Type: "mcq", Prompt: "What is?", Options: []string{"A", "B"}, Correct: "A"},
					}},
				},
			},
		},
	}

	prompt := ai.BuildPrompt(raw)

	checks := []string{"Cambridge 18", "reading", "mcq", "quality_score", "has_reading"}
	for _, s := range checks {
		found := false
		for i := 0; i <= len(prompt)-len(s); i++ {
			if prompt[i:i+len(s)] == s {
				found = true
				break
			}
		}
		if !found {
			t.Errorf("prompt missing %q", s)
		}
	}
}
