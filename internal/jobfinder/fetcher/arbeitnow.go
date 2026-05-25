package fetcher

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"
)

type ArbeitnowFetcher struct {
	client *http.Client
}

func NewArbeitnowFetcher() *ArbeitnowFetcher {
	return &ArbeitnowFetcher{client: &http.Client{Timeout: 15 * time.Second}}
}

func (f *ArbeitnowFetcher) Name() string { return "arbeitnow" }

func (f *ArbeitnowFetcher) Fetch() ([]RawJob, error) {
	resp, err := f.client.Get("https://arbeitnow.com/api/job-board-api")
	if err != nil {
		return nil, fmt.Errorf("arbeitnow fetch: %w", err)
	}
	defer resp.Body.Close()

	var result struct {
		Data []struct {
			Title       string   `json:"title"`
			CompanyName string   `json:"company_name"`
			Location    string   `json:"location"`
			Description string   `json:"description"`
			URL         string   `json:"url"`
			Remote      bool     `json:"remote"`
			Tags        []string `json:"tags"`
		} `json:"data"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, fmt.Errorf("arbeitnow decode: %w", err)
	}

	mobileKeywords := []string{"flutter", "mobile", "ios", "dart", "react native", "android"}
	var jobs []RawJob
	for _, j := range result.Data {
		if !isMobileJob(j.Title, j.Tags, mobileKeywords) {
			continue
		}
		loc := j.Location
		if j.Remote {
			loc = "Remote"
		}
		jobs = append(jobs, RawJob{
			Title:       j.Title,
			Company:     j.CompanyName,
			Location:    loc,
			Description: stripHTML(j.Description),
			ApplyURL:    j.URL,
			Source:      "arbeitnow",
		})
	}
	return jobs, nil
}

func isMobileJob(title string, tags []string, keywords []string) bool {
	titleLower := strings.ToLower(title)
	for _, kw := range keywords {
		if strings.Contains(titleLower, kw) {
			return true
		}
	}
	for _, tag := range tags {
		tagLower := strings.ToLower(tag)
		for _, kw := range keywords {
			if strings.Contains(tagLower, kw) {
				return true
			}
		}
	}
	return false
}
