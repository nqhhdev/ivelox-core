package fetcher

// RawJob is a job listing as returned by any source before AI scoring.
type RawJob struct {
	Title       string
	Company     string
	Location    string
	Salary      string
	Description string
	ApplyURL    string
	Source      string // "remotive" | "arbeitnow" | "themuse" | "topdev" | "itviec"
}

// Fetcher is implemented by each job source.
type Fetcher interface {
	Fetch() ([]RawJob, error)
	Name() string
}
