package scraper_test

import (
	"strings"
	"testing"

	"github.com/nqhhdev/ivelox-core/internal/scraper"
)

func TestParseIOT_ExtractsExamIndex(t *testing.T) {
	html := `<html><body>
<div class="exam-list">
  <a class="exam-item" href="/ielts-reading-practice-test-1">IELTS Reading Practice Test 1</a>
  <a class="exam-item" href="/ielts-listening-practice-test-1">IELTS Listening Practice Test 1</a>
</div>
</body></html>`

	links := scraper.ParseIOTExamLinks(strings.NewReader(html))
	if len(links) < 2 {
		t.Errorf("expected >= 2 links got %d", len(links))
	}
}
