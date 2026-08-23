package dedup_test

import (
	"testing"

	"github.com/nqhhdev/ivelox-core/internal/jobfinder/dedup"
)

func TestHash_Consistent(t *testing.T) {
	url := "https://example.com/job/123"
	h1 := dedup.Hash(url)
	h2 := dedup.Hash(url)
	if h1 != h2 {
		t.Fatalf("hash not consistent: %s vs %s", h1, h2)
	}
	if len(h1) != 32 {
		t.Fatalf("expected 32-char md5, got %d: %s", len(h1), h1)
	}
}

func TestHash_Different(t *testing.T) {
	h1 := dedup.Hash("https://example.com/job/1")
	h2 := dedup.Hash("https://example.com/job/2")
	if h1 == h2 {
		t.Fatal("different URLs should produce different hashes")
	}
}
