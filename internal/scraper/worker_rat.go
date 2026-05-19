package scraper

import (
	"context"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/PuerkitoBio/goquery"
)

var ratSiteURLs = []struct {
	base  string
	index string
}{
	{base: "https://onthiielts.com.vn", index: "/luyen-thi-ielts/de-thi-ielts-recent-actual-test"},
	{base: "https://ielts-fighter.com", index: "/tai-lieu/de-thi-ielts-actual-test.html"},
}

func ScrapeRATSites(ctx context.Context) ([]*RawExam, error) {
	client := &http.Client{Timeout: 30 * time.Second}
	var results []*RawExam

	for _, site := range ratSiteURLs {
		indexURL := site.base + site.index
		resp, err := fetchWithUA(ctx, client, indexURL)
		if err != nil {
			fmt.Printf("[rat] index error %s: %v\n", indexURL, err)
			continue
		}

		doc, err := goquery.NewDocumentFromReader(resp.Body)
		resp.Body.Close()
		if err != nil {
			continue
		}

		var testLinks []string
		doc.Find("a[href]").Each(func(_ int, s *goquery.Selection) {
			href, _ := s.Attr("href")
			text := strings.ToLower(s.Text())
			if strings.Contains(text, "actual") || strings.Contains(text, "test") || strings.Contains(href, "actual-test") {
				if !strings.HasPrefix(href, "http") {
					href = site.base + href
				}
				testLinks = append(testLinks, href)
			}
		})

		for _, link := range testLinks {
			raw, err := fetchAndParseRAT(ctx, client, link)
			if err != nil {
				fmt.Printf("[rat] skip %s: %v\n", link, err)
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
	}
	return results, nil
}

func fetchAndParseRAT(ctx context.Context, client *http.Client, url string) (*RawExam, error) {
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
		SourceName: "rat_sites",
		Skills:     map[string]*RawSkill{},
	}

	for _, skill := range []string{"reading", "listening", "writing", "speaking"} {
		selector := fmt.Sprintf("[class*='%s'], [id*='%s']", skill, skill)
		skillSection := doc.Find(selector).First()
		if skillSection.Length() == 0 {
			continue
		}

		rawSkill := &RawSkill{Skill: skill}
		section := &RawSection{Position: 1}
		section.Content = strings.TrimSpace(skillSection.Find("p, .passage, .content").First().Text())

		skillSection.Find(".question, [class*='question']").Each(func(i int, s *goquery.Selection) {
			prompt := strings.TrimSpace(s.Find("p, .stem").First().Text())
			if prompt == "" {
				return
			}
			q := &RawQuestion{
				Position: i + 1,
				Type:     "mcq",
				Prompt:   prompt,
			}
			s.Find("li, .choice, .option").Each(func(_ int, o *goquery.Selection) {
				q.Options = append(q.Options, strings.TrimSpace(o.Text()))
			})
			section.Questions = append(section.Questions, q)
		})

		if len(section.Questions) > 0 || section.Content != "" {
			rawSkill.Sections = append(rawSkill.Sections, section)
			raw.Skills[skill] = rawSkill
		}
	}

	if len(raw.Skills) == 0 {
		return nil, nil
	}
	return raw, nil
}
