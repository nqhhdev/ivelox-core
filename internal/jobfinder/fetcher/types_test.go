package fetcher_test

import (
	"testing"

	"github.com/nqhhdev/ivelox-core/internal/jobfinder/fetcher"
)

func TestRawJob_Fields(t *testing.T) {
	j := fetcher.RawJob{
		Title:    "Senior Flutter Developer",
		Company:  "Grab",
		Source:   "remotive",
		ApplyURL: "https://example.com/job/1",
	}
	if j.Title != "Senior Flutter Developer" {
		t.Fatalf("unexpected title: %s", j.Title)
	}
	if j.Source != "remotive" {
		t.Fatalf("unexpected source: %s", j.Source)
	}
}
