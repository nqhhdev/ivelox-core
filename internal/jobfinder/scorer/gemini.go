package scorer

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
	"sync"
	"time"

	"github.com/google/generative-ai-go/genai"
	"github.com/nqhhdev/ivelox-core/internal/jobfinder/fetcher"
	"google.golang.org/api/option"
)

// Scorer scores jobs against a candidate profile using Gemini.
type Scorer struct {
	client  *genai.Client
	model   string
	mu      sync.RWMutex
	profile string // candidate profile text; updated via SetProfile
}

func NewScorer(ctx context.Context, apiKey string) (*Scorer, error) {
	client, err := genai.NewClient(ctx, option.WithAPIKey(apiKey))
	if err != nil {
		return nil, fmt.Errorf("gemini client: %w", err)
	}
	return &Scorer{client: client, model: "gemini-2.5-flash-lite"}, nil
}

func (s *Scorer) Close() {
	s.client.Close()
}

// SetProfile updates the candidate profile text used for scoring.
func (s *Scorer) SetProfile(profileText string) {
	s.mu.Lock()
	s.profile = profileText
	s.mu.Unlock()
}

// ScoreResult is the raw JSON response from Gemini.
type ScoreResult struct {
	Score        int      `json:"score"`
	MatchReasons []string `json:"match_reasons"`
	GapSkills    []string `json:"gap_skills"`
	WorkType     string   `json:"work_type"`
	Seniority    string   `json:"seniority"`
}

// Score evaluates a single job and returns a ScoredJob.
// Returns nil if score < threshold.
func (s *Scorer) Score(ctx context.Context, job fetcher.RawJob, threshold int) (*ScoredJob, error) {
	s.mu.RLock()
	profile := s.profile
	s.mu.RUnlock()
	prompt := buildPrompt(job, profile)

	model := s.client.GenerativeModel(s.model)
	model.SetTemperature(0.1)

	ctx, cancel := context.WithTimeout(ctx, 30*time.Second)
	defer cancel()

	resp, err := model.GenerateContent(ctx, genai.Text(prompt))
	if err != nil {
		return nil, fmt.Errorf("gemini generate: %w", err)
	}

	if len(resp.Candidates) == 0 || resp.Candidates[0].Content == nil || len(resp.Candidates[0].Content.Parts) == 0 {
		return nil, fmt.Errorf("gemini empty response")
	}

	raw := fmt.Sprintf("%v", resp.Candidates[0].Content.Parts[0])
	raw = extractJSON(raw)

	var result ScoreResult
	if err := json.Unmarshal([]byte(raw), &result); err != nil {
		return nil, fmt.Errorf("gemini json parse: %w (raw: %s)", err, raw)
	}

	if result.Score < threshold {
		return nil, nil
	}

	return &ScoredJob{
		RawJob:       job,
		Score:        result.Score,
		MatchReasons: result.MatchReasons,
		GapSkills:    result.GapSkills,
		WorkType:     result.WorkType,
		Seniority:    result.Seniority,
	}, nil
}

func buildPrompt(job fetcher.RawJob, profile string) string {
	desc := job.Description
	if len(desc) > 2000 {
		desc = desc[:2000]
	}
	return fmt.Sprintf(`You are a job matching assistant. Score how well this job matches the candidate profile.

CANDIDATE PROFILE:
%s

JOB LISTING:
Title: %s
Company: %s
Location: %s
Salary: %s
Description: %s

Respond in JSON only, no markdown, no explanation:
{
  "score": 0-100,
  "match_reasons": ["reason1", "reason2"],
  "gap_skills": ["skill1"],
  "work_type": "remote|hybrid|onsite|unknown",
  "seniority": "junior|mid|senior|lead|unknown"
}

Scoring guide:
- 80-100: Strong match, apply immediately
- 60-79: Good match, worth considering
- 40-59: Partial match, missing key requirements
- 0-39: Poor match, skip`,
		profile, job.Title, job.Company, job.Location, job.Salary, desc)
}

// extractJSON pulls the JSON object from a string that may contain surrounding text.
func extractJSON(s string) string {
	start := strings.Index(s, "{")
	end := strings.LastIndex(s, "}")
	if start == -1 || end == -1 || end <= start {
		return s
	}
	return s[start : end+1]
}
