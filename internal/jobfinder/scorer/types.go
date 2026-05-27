package scorer

import "github.com/nqhhdev/ivelox-core/internal/jobfinder/fetcher"

// ScoredJob is a RawJob after AI evaluation.
type ScoredJob struct {
	fetcher.RawJob
	Score        int      // 0–100
	MatchReasons []string // why this job matches the profile
	GapSkills    []string // skills in JD not in profile
	WorkType     string   // "remote" | "hybrid" | "onsite" | "unknown"
	Seniority    string   // "junior" | "mid" | "senior" | "lead" | "unknown"
}
