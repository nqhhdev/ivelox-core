package fetcher

import (
	"fmt"
	"net/http"
	"strings"
	"time"

	"golang.org/x/net/html"
)

type TopDevFetcher struct {
	client *http.Client
}

func NewTopDevFetcher() *TopDevFetcher {
	return &TopDevFetcher{client: &http.Client{Timeout: 20 * time.Second}}
}

func (f *TopDevFetcher) Name() string { return "topdev" }

func (f *TopDevFetcher) Fetch() ([]RawJob, error) {
	keywords := []string{"flutter", "mobile", "ios"}
	var all []RawJob
	for _, kw := range keywords {
		jobs, err := f.fetchKeyword(kw)
		if err != nil {
			fmt.Printf("[topdev] keyword %q error: %v\n", kw, err)
			continue
		}
		all = append(all, jobs...)
	}
	return dedupeByURL(all), nil
}

func (f *TopDevFetcher) fetchKeyword(keyword string) ([]RawJob, error) {
	url := fmt.Sprintf("https://topdev.vn/it-jobs?q=%s", keyword)
	req, _ := http.NewRequest("GET", url, nil)
	req.Header.Set("User-Agent", "Mozilla/5.0 (compatible; jobfinder/1.0)")

	resp, err := f.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	doc, err := html.Parse(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("topdev parse html: %w", err)
	}

	var jobs []RawJob
	var walk func(*html.Node)
	walk = func(n *html.Node) {
		if n.Type == html.ElementNode && n.Data == "div" {
			for _, a := range n.Attr {
				if a.Key == "class" && strings.Contains(a.Val, "job-item") {
					job := extractTopDevJob(n)
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

func extractTopDevJob(n *html.Node) RawJob {
	var job RawJob
	job.Source = "topdev"

	var walk func(*html.Node)
	walk = func(n *html.Node) {
		if n.Type == html.ElementNode {
			switch n.Data {
			case "a":
				for _, a := range n.Attr {
					if a.Key == "href" && strings.Contains(a.Val, "/it-jobs/") {
						if !strings.HasPrefix(a.Val, "http") {
							job.ApplyURL = "https://topdev.vn" + a.Val
						} else {
							job.ApplyURL = a.Val
						}
					}
				}
				if job.Title == "" {
					job.Title = strings.TrimSpace(nodeText(n))
				}
			case "span":
				for _, a := range n.Attr {
					if a.Key == "class" && strings.Contains(a.Val, "company") {
						job.Company = strings.TrimSpace(nodeText(n))
					}
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

func nodeText(n *html.Node) string {
	var b strings.Builder
	var walk func(*html.Node)
	walk = func(n *html.Node) {
		if n.Type == html.TextNode {
			b.WriteString(n.Data)
		}
		for c := n.FirstChild; c != nil; c = c.NextSibling {
			walk(c)
		}
	}
	walk(n)
	return b.String()
}
