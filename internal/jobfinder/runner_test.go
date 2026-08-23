package jobfinder_test

import (
	"testing"

	jobfinder "github.com/nqhhdev/ivelox-core/internal/jobfinder"
	"github.com/nqhhdev/ivelox-core/internal/jobfinder/fetcher"
)

// fakeFetcher returns a fixed set of jobs.
type fakeFetcher struct {
	name string
	jobs []fetcher.RawJob
	err  error
}

func (f *fakeFetcher) Name() string                    { return f.name }
func (f *fakeFetcher) Fetch() ([]fetcher.RawJob, error) { return f.jobs, f.err }

func TestFakeFetcher_Interface(t *testing.T) {
	var _ fetcher.Fetcher = &fakeFetcher{}
}

// Ensure NewRunner is accessible and the package compiles.
var _ = jobfinder.NewRunner
