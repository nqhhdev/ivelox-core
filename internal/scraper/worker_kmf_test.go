package scraper_test

import (
	"strings"
	"testing"

	"github.com/nqhhdev/ivelox-core/internal/scraper"
)

func TestParseKMFReading_ExtractsQuestions(t *testing.T) {
	html := `<html><body>
<div class="passage-content">Antarctica is the coldest continent on Earth.</div>
<div class="question-list">
  <div class="question-item" data-position="1" data-type="mcq">
    <div class="question-stem">What is Antarctica?</div>
    <ul class="options">
      <li data-key="A">A continent</li>
      <li data-key="B">An ocean</li>
    </ul>
    <div class="answer">A</div>
  </div>
</div>
</body></html>`

	raw := scraper.ParseKMFHTML("https://ielts.kmf.com/test/1", "reading", strings.NewReader(html))

	if raw == nil {
		t.Fatal("expected non-nil RawExam")
	}
	rs := raw.Skills["reading"]
	if rs == nil || len(rs.Sections) == 0 {
		t.Fatal("expected reading sections")
	}
	if len(rs.Sections[0].Questions) == 0 {
		t.Fatal("expected questions")
	}
	q := rs.Sections[0].Questions[0]
	if q.Correct != "A" {
		t.Errorf("expected correct=A got %s", q.Correct)
	}
	if len(q.Options) != 2 {
		t.Errorf("expected 2 options got %d", len(q.Options))
	}
}
