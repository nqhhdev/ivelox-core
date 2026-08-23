package fetcher

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"
)

var remotiveKeywords = []string{
	"flutter", "mobile", "ios", "dart", "react-native",
}

type RemotiveFetcher struct {
	client *http.Client
}

func NewRemotiveFetcher() *RemotiveFetcher {
	return &RemotiveFetcher{client: &http.Client{Timeout: 15 * time.Second}}
}

func (f *RemotiveFetcher) Name() string { return "remotive" }

func (f *RemotiveFetcher) Fetch() ([]RawJob, error) {
	var all []RawJob
	for _, kw := range remotiveKeywords {
		jobs, err := f.fetchKeyword(kw)
		if err != nil {
			fmt.Printf("[remotive] keyword %q error: %v\n", kw, err)
			continue
		}
		all = append(all, jobs...)
	}
	return dedupeByURL(all), nil
}

func (f *RemotiveFetcher) fetchKeyword(keyword string) ([]RawJob, error) {
	url := fmt.Sprintf("https://remotive.com/api/remote-jobs?search=%s&limit=20", keyword)
	resp, err := f.client.Get(url)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("remotive: status %d", resp.StatusCode)
	}

	var result struct {
		Jobs []struct {
			Title       string `json:"title"`
			CompanyName string `json:"company_name"`
			Location    string `json:"candidate_required_location"`
			Salary      string `json:"salary"`
			Description string `json:"description"`
			URL         string `json:"url"`
		} `json:"jobs"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, err
	}

	jobs := make([]RawJob, 0, len(result.Jobs))
	for _, j := range result.Jobs {
		jobs = append(jobs, RawJob{
			Title:       j.Title,
			Company:     j.CompanyName,
			Location:    j.Location,
			Salary:      j.Salary,
			Description: stripHTML(j.Description),
			ApplyURL:    j.URL,
			Source:      "remotive",
		})
	}
	return jobs, nil
}

// dedupeByURL removes duplicate jobs with the same ApplyURL within a single fetch result.
func dedupeByURL(jobs []RawJob) []RawJob {
	seen := make(map[string]bool)
	out := make([]RawJob, 0, len(jobs))
	for _, j := range jobs {
		if !seen[j.ApplyURL] {
			seen[j.ApplyURL] = true
			out = append(out, j)
		}
	}
	return out
}

// stripHTML removes HTML tags from a string for clean AI input.
func stripHTML(s string) string {
	var b strings.Builder
	inTag := false
	for _, r := range s {
		switch {
		case r == '<':
			inTag = true
		case r == '>':
			inTag = false
		case !inTag:
			b.WriteRune(r)
		}
	}
	return strings.TrimSpace(b.String())
}
