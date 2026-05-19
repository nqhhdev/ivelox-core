package scraper

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/PuerkitoBio/goquery"
)

const iotBaseURL = "https://ieltsonlinetests.com"

func ScrapeIeltsOnlineTests(ctx context.Context) ([]*RawExam, error) {
	client := &http.Client{Timeout: 30 * time.Second}
	var results []*RawExam

	indexURL := iotBaseURL + "/ielts-exam-library"
	resp, err := fetchWithUA(ctx, client, indexURL)
	if err != nil {
		return nil, fmt.Errorf("[ieltsonlinetests] index: %w", err)
	}
	links := ParseIOTExamLinks(resp.Body)
	resp.Body.Close()

	for _, link := range links {
		url := iotBaseURL + link
		raw, err := fetchAndParseIOT(ctx, client, url)
		if err != nil {
			fmt.Printf("[ieltsonlinetests] skip %s: %v\n", url, err)
			continue
		}
		if raw != nil {
			results = append(results, raw)
		}
		select {
		case <-ctx.Done():
			return results, nil
		case <-time.After(3 * time.Second):
		}
	}
	return results, nil
}

// ParseIOTExamLinks parses the exam library index and returns relative URLs.
// Exported for testability.
func ParseIOTExamLinks(r io.Reader) []string {
	doc, err := goquery.NewDocumentFromReader(r)
	if err != nil {
		return nil
	}
	var links []string
	doc.Find("a[href]").Each(func(_ int, s *goquery.Selection) {
		href, _ := s.Attr("href")
		if strings.Contains(href, "practice-test") || strings.Contains(href, "mock-test") {
			links = append(links, href)
		}
	})
	return links
}

func fetchAndParseIOT(ctx context.Context, client *http.Client, url string) (*RawExam, error) {
	resp, err := fetchWithUA(ctx, client, url)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("status %d", resp.StatusCode)
	}

	doc, err := goquery.NewDocumentFromReader(resp.Body)
	if err != nil {
		return nil, err
	}

	raw := &RawExam{
		SourceURL:  url,
		SourceName: "ieltsonlinetests",
		Skills:     map[string]*RawSkill{},
	}

	title := strings.ToLower(doc.Find("h1, title").First().Text())
	skill := detectSkillFromTitle(title)
	if skill == "" {
		return nil, nil
	}

	skillData := &RawSkill{Skill: skill}
	raw.Skills[skill] = skillData
	section := &RawSection{Position: 1}

	doc.Find(".passage, .reading-text, [class*='passage']").Each(func(_ int, s *goquery.Selection) {
		if section.Content == "" {
			section.Content = strings.TrimSpace(s.Text())
		}
	})

	doc.Find(".question, [class*='question-wrap']").Each(func(i int, s *goquery.Selection) {
		prompt := strings.TrimSpace(s.Find(".question-content, p").First().Text())
		if prompt == "" {
			return
		}
		var options []string
		s.Find("li, .choice").Each(func(_ int, o *goquery.Selection) {
			options = append(options, strings.TrimSpace(o.Text()))
		})
		q := &RawQuestion{
			Position: i + 1,
			Type:     "mcq",
			Prompt:   prompt,
			Options:  options,
		}
		section.Questions = append(section.Questions, q)
	})

	if len(section.Questions) == 0 {
		return nil, nil
	}
	skillData.Sections = append(skillData.Sections, section)
	return raw, nil
}

func detectSkillFromTitle(title string) string {
	switch {
	case strings.Contains(title, "reading"):
		return "reading"
	case strings.Contains(title, "listening"):
		return "listening"
	case strings.Contains(title, "writing"):
		return "writing"
	case strings.Contains(title, "speaking"):
		return "speaking"
	default:
		return ""
	}
}

func fetchWithUA(ctx context.Context, client *http.Client, url string) (*http.Response, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (compatible; iVelox-research/1.0)")
	return client.Do(req)
}
