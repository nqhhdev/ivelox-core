package scraper

import (
	"bufio"
	"context"
	"fmt"
	"io"
	"net/http"
	"regexp"
	"strings"
)

var archiveURLs = []string{
	"https://archive.org/stream/complete-ielts-band-55-65/Complete%20IELTS%20band%2055-65_djvu.txt",
	"https://archive.org/stream/OfficialIeltsPracticeMaterials2/Official_Ielts_Practice_Materials_2_djvu.txt",
}

func ScrapeArchiveOrg(ctx context.Context) ([]*RawExam, error) {
	var results []*RawExam
	client := &http.Client{}

	for _, url := range archiveURLs {
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
		if err != nil {
			continue
		}
		resp, err := client.Do(req)
		if err != nil {
			fmt.Printf("[archive.org] fetch error %s: %v\n", url, err)
			continue
		}
		raw := ParseArchiveDjvuText(url, resp.Body)
		resp.Body.Close()
		if raw != nil {
			results = append(results, raw)
		}
	}
	return results, nil
}

var (
	rePassageHeader = regexp.MustCompile(`(?i)READING PASSAGE\s+(\d+)`)
	reQuestionNum   = regexp.MustCompile(`^\s*(\d+)\s+(.+)`)
	reAnswerKey     = regexp.MustCompile(`(?i)ANSWER\s+KEY`)
	reAnswerLine    = regexp.MustCompile(`^\s*(\d+)\s+([A-Z](?:\s*[A-Z])?)\s*$`)
	reMCQOption     = regexp.MustCompile(`^[A-D]\s+`)
)

// ParseArchiveDjvuText parses a djvu.txt stream into a RawExam.
// Exported for testability.
func ParseArchiveDjvuText(sourceURL string, r io.Reader) *RawExam {
	raw := &RawExam{
		SourceURL:  sourceURL,
		SourceName: "archive_org",
		Skills:     map[string]*RawSkill{},
	}

	readingSkill := &RawSkill{Skill: "reading"}
	raw.Skills["reading"] = readingSkill

	scanner := bufio.NewScanner(r)
	var currentSection *RawSection
	var passageLines []string
	inPassage := false
	inAnswerKey := false
	answers := map[int]string{}

	for scanner.Scan() {
		line := scanner.Text()

		if reAnswerKey.MatchString(line) {
			if currentSection != nil && len(passageLines) > 0 {
				currentSection.Content = strings.TrimSpace(strings.Join(passageLines, "\n"))
			}
			inPassage = false
			inAnswerKey = true
			continue
		}

		if inAnswerKey {
			if m := reAnswerLine.FindStringSubmatch(line); m != nil {
				var qNum int
				fmt.Sscanf(m[1], "%d", &qNum)
				answers[qNum] = strings.TrimSpace(m[2])
			}
			continue
		}

		if m := rePassageHeader.FindStringSubmatch(line); m != nil {
			if currentSection != nil && len(passageLines) > 0 {
				currentSection.Content = strings.TrimSpace(strings.Join(passageLines, "\n"))
			}
			var pos int
			fmt.Sscanf(m[1], "%d", &pos)
			currentSection = &RawSection{Position: pos}
			readingSkill.Sections = append(readingSkill.Sections, currentSection)
			passageLines = nil
			inPassage = true
			continue
		}

		if inPassage && currentSection != nil {
			if reQuestionNum.MatchString(line) && !reMCQOption.MatchString(line) {
				inPassage = false
			} else {
				passageLines = append(passageLines, line)
				continue
			}
		}

		if currentSection != nil && !inPassage {
			if m := reQuestionNum.FindStringSubmatch(line); m != nil {
				var pos int
				fmt.Sscanf(m[1], "%d", &pos)
				q := &RawQuestion{
					Position: pos,
					Type:     "mcq",
					Prompt:   strings.TrimSpace(m[2]),
				}
				currentSection.Questions = append(currentSection.Questions, q)
			}
		}
	}

	if currentSection != nil && len(passageLines) > 0 && currentSection.Content == "" {
		currentSection.Content = strings.TrimSpace(strings.Join(passageLines, "\n"))
	}

	for _, sec := range readingSkill.Sections {
		for _, q := range sec.Questions {
			if ans, ok := answers[q.Position]; ok {
				q.Correct = ans
			}
		}
	}

	if len(readingSkill.Sections) == 0 {
		return nil
	}
	return raw
}
