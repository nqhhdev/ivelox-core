package scraper_test

import (
	"strings"
	"testing"

	"github.com/nqhhdev/ivelox-core/internal/scraper"
)

func TestParseArchiveDjvu_ExtractsPassage(t *testing.T) {
	sample := `
READING PASSAGE 1

You should spend about 20 minutes on Questions 1-13.

The Lost City

Archaeologists have long debated the origins of ancient cities.
Many believe that trade routes played a crucial role.

Questions 1-5
Choose the correct letter, A, B, C or D.

1 The main argument of the passage is that
A trade was the primary factor in city development
B geography determined city locations
C culture shaped early settlements
D technology enabled urban growth

ANSWER KEY
1 A
`

	raw := scraper.ParseArchiveDjvuText("https://archive.org/test", strings.NewReader(sample))

	if raw == nil {
		t.Fatal("expected non-nil RawExam")
	}
	rs, ok := raw.Skills["reading"]
	if !ok {
		t.Fatal("expected reading skill")
	}
	if len(rs.Sections) == 0 {
		t.Fatal("expected at least 1 section")
	}
	if rs.Sections[0].Content == "" {
		t.Error("expected passage content")
	}
	if len(rs.Sections[0].Questions) == 0 {
		t.Error("expected at least 1 question")
	}
	if rs.Sections[0].Questions[0].Correct != "A" {
		t.Errorf("expected correct=A got %s", rs.Sections[0].Questions[0].Correct)
	}
}
