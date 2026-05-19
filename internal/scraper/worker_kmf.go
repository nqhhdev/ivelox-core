package scraper

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/PuerkitoBio/goquery"
)

func ScrapeKMF(ctx context.Context) ([]*RawExam, error) {
	client := &http.Client{Timeout: 30 * time.Second}
	var results []*RawExam

	for bookNum := 4; bookNum <= 18; bookNum++ {
		for testNum := 1; testNum <= 4; testNum++ {
			url := fmt.Sprintf("https://ielts.kmf.com/reading/cambridge-%d/test-%d", bookNum, testNum)
			raw, err := fetchAndParseKMF(ctx, client, url, bookNum, testNum)
			if err != nil {
				fmt.Printf("[kmf] skip %s: %v\n", url, err)
				continue
			}
			if raw != nil {
				results = append(results, raw)
			}
			select {
			case <-ctx.Done():
				return results, nil
			case <-time.After(2 * time.Second):
			}
		}
	}
	return results, nil
}

func fetchAndParseKMF(ctx context.Context, client *http.Client, url string, bookNum, testNum int) (*RawExam, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (compatible; iVelox-research/1.0)")
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("status %d", resp.StatusCode)
	}

	raw := ParseKMFHTML(url, "reading", resp.Body)
	if raw == nil {
		return nil, nil
	}
	raw.Series = fmt.Sprintf("Cambridge %d", bookNum)
	raw.TestNumber = testNum
	raw.Year = 2000 + bookNum
	return raw, nil
}

// ParseKMFHTML parses a kmf.com test page HTML into a RawExam.
// Exported for testability.
func ParseKMFHTML(sourceURL, skill string, r io.Reader) *RawExam {
	doc, err := goquery.NewDocumentFromReader(r)
	if err != nil {
		return nil
	}

	raw := &RawExam{
		SourceURL:  sourceURL,
		SourceName: "kmf",
		Skills:     map[string]*RawSkill{},
	}

	skillData := &RawSkill{Skill: skill}
	raw.Skills[skill] = skillData
	section := &RawSection{Position: 1}

	doc.Find(".passage-content, .reading-passage, [class*='passage']").Each(func(i int, s *goquery.Selection) {
		if section.Content == "" {
			section.Content = strings.TrimSpace(s.Text())
		}
	})

	doc.Find(".question-item, [class*='question-item'], .question").Each(func(i int, s *goquery.Selection) {
		posStr, _ := s.Attr("data-position")
		pos, _ := strconv.Atoi(posStr)
		if pos == 0 {
			pos = i + 1
		}

		qType, _ := s.Attr("data-type")
		if qType == "" {
			qType = "mcq"
		}

		prompt := strings.TrimSpace(s.Find(".question-stem, .stem, [class*='stem']").First().Text())
		if prompt == "" {
			prompt = strings.TrimSpace(s.Find("p").First().Text())
		}

		var options []string
		s.Find(".options li").Each(func(_ int, opt *goquery.Selection) {
			key, _ := opt.Attr("data-key")
			text := strings.TrimSpace(opt.Text())
			if key != "" {
				options = append(options, key+". "+text)
			} else {
				options = append(options, text)
			}
		})

		correct := strings.TrimSpace(s.Find(".answer, [class*='answer']").Text())
		explanation := strings.TrimSpace(s.Find(".explanation, [class*='explanation']").Text())

		q := &RawQuestion{
			Position:    pos,
			Type:        qType,
			Prompt:      prompt,
			Options:     options,
			Correct:     correct,
			Explanation: explanation,
		}
		section.Questions = append(section.Questions, q)
	})

	if len(section.Questions) == 0 && section.Content == "" {
		return nil
	}

	skillData.Sections = append(skillData.Sections, section)
	return raw
}
