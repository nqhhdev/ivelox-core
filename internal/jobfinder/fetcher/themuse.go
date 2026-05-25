package fetcher

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"
)

type TheMuseFetcher struct {
	client *http.Client
}

func NewTheMuseFetcher() *TheMuseFetcher {
	return &TheMuseFetcher{client: &http.Client{Timeout: 15 * time.Second}}
}

func (f *TheMuseFetcher) Name() string { return "themuse" }

func (f *TheMuseFetcher) Fetch() ([]RawJob, error) {
	urls := []string{
		"https://www.themuse.com/api/public/jobs?category=Engineering&level=Senior+Level&page=0&descending=true",
		"https://www.themuse.com/api/public/jobs?category=Engineering&level=Mid+Level&page=0&descending=true",
	}

	mobileKeywords := []string{"flutter", "mobile", "ios", "dart", "react native", "android", "cross-platform"}
	var all []RawJob

	for _, u := range urls {
		jobs, err := f.fetchPage(u, mobileKeywords)
		if err != nil {
			fmt.Printf("[themuse] error fetching %s: %v\n", u, err)
			continue
		}
		all = append(all, jobs...)
	}
	return dedupeByURL(all), nil
}

func (f *TheMuseFetcher) fetchPage(url string, keywords []string) ([]RawJob, error) {
	resp, err := f.client.Get(url)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	var result struct {
		Results []struct {
			Name    string          `json:"name"`
			Company struct{ Name string } `json:"company"`
			Locations []struct{ Name string } `json:"locations"`
			Contents string `json:"contents"`
			Refs     struct {
				LandingPage string `json:"landing_page"`
			} `json:"refs"`
		} `json:"results"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, err
	}

	var jobs []RawJob
	for _, j := range result.Results {
		if !containsAny(strings.ToLower(j.Name), keywords) &&
			!containsAny(strings.ToLower(j.Contents), keywords) {
			continue
		}
		loc := "Unknown"
		if len(j.Locations) > 0 {
			loc = j.Locations[0].Name
		}
		jobs = append(jobs, RawJob{
			Title:       j.Name,
			Company:     j.Company.Name,
			Location:    loc,
			Description: stripHTML(j.Contents),
			ApplyURL:    j.Refs.LandingPage,
			Source:      "themuse",
		})
	}
	return jobs, nil
}

func containsAny(s string, keywords []string) bool {
	for _, kw := range keywords {
		if strings.Contains(s, kw) {
			return true
		}
	}
	return false
}
