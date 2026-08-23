package health_test

import (
	"testing"

	"github.com/nqhhdev/ivelox-core/internal/health"
)

func TestNormalizeFoodName(t *testing.T) {
	cases := map[string]string{
		"  Phở Bò  ": "pho bo",
		"CƠM TẤM":    "com tam",
		"Apple Pie":  "apple pie",
	}
	for in, want := range cases {
		got := health.NormalizeFoodName(in)
		if got != want {
			t.Fatalf("NormalizeFoodName(%q)=%q want %q", in, got, want)
		}
	}
}
