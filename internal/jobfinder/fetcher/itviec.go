package fetcher

import (
	"fmt"
	"net/http"
	"strings"
	"time"

	"golang.org/x/net/html"
)

type ITviecFetcher struct {
	client *http.Client
}

func NewITviecFetcher() *ITviecFetcher {
	return &ITviecFetcher{client: &http.Client{Timeout: 20 * time.Second}}
}

func (f *ITviecFetcher) Name() string { return "itviec" }

func (f *ITviecFetcher) Fetch() ([]RawJob, error) {
	keywords := []string{"flutter", "mobile", "ios"}
	var all []RawJob
	for _, kw := range keywords {
		jobs, err := f.fetchKeyword(kw)
		if err != nil {
			fmt.Printf("[itviec] keyword %q error: %v\n", kw, err)
			continue
		}
		all = append(all, jobs...)
	}
	return dedupeByURL(all), nil
}

func (f *ITviecFetcher) fetchKeyword(keyword string) ([]RawJob, error) {
	url := fmt.Sprintf("https://itviec.com/it-jobs/%s", keyword)
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, fmt.Errorf("itviec new request: %w", err)
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (compatible; jobfinder/1.0)")
	req.Header.Set("Accept-Language", "en-US,en;q=0.9")

	resp, err := f.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("itviec: status %d", resp.StatusCode)
	}

	doc, err := html.Parse(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("itviec parse html: %w", err)
	}

	var jobs []RawJob
	var walk func(*html.Node)
	walk = func(n *html.Node) {
		if n.Type == html.ElementNode && n.Data == "div" {
			for _, a := range n.Attr {
				if a.Key == "class" && strings.Contains(a.Val, "job-card") {
					job := extractITviecJob(n)
					if job.Title != "" && job.ApplyURL != "" {
						jobs = append(jobs, job)
					}
				}
			}
		}
		for c := n.FirstChild; c != nil; c = c.NextSibling {
			walk(c)
		}
	}
	walk(doc)
	return jobs, nil
}

func extractITviecJob(n *html.Node) RawJob {
	var job RawJob
	job.Source = "itviec"

	var walk func(*html.Node)
	walk = func(n *html.Node) {
		if n.Type == html.ElementNode {
			switch n.Data {
			case "a":
				for _, a := range n.Attr {
					if a.Key == "class" && strings.Contains(a.Val, "job-title") {
						if job.Title == "" {
							job.Title = strings.TrimSpace(nodeText(n))
						}
					}
					if a.Key == "href" && strings.Contains(a.Val, "/it-jobs/") {
						if !strings.HasPrefix(a.Val, "http") {
							job.ApplyURL = "https://itviec.com" + a.Val
						} else {
							job.ApplyURL = a.Val
						}
					}
				}
			case "img":
				for _, a := range n.Attr {
					if a.Key == "alt" && job.Company == "" {
						job.Company = strings.TrimSpace(a.Val)
					}
				}
			case "span":
				for _, a := range n.Attr {
					if a.Key == "class" && strings.Contains(a.Val, "location") {
						job.Location = strings.TrimSpace(nodeText(n))
					}
				}
			}
		}
		for c := n.FirstChild; c != nil; c = c.NextSibling {
			walk(c)
		}
	}
	walk(n)
	return job
}
