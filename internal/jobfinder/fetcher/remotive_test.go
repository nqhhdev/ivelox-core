package fetcher_test

import (
	"testing"

	"github.com/nqhhdev/ivelox-core/internal/jobfinder/fetcher"
)

func TestRemotiveFetcher_Name(t *testing.T) {
	f := fetcher.NewRemotiveFetcher()
	if f.Name() != "remotive" {
		t.Fatalf("expected 'remotive', got %s", f.Name())
	}
}

func TestStripHTML(t *testing.T) {
	t.Skip("integration: requires network")
}
