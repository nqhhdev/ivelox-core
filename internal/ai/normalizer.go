package ai

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"

	openai "github.com/sashabaranov/go-openai"
	"github.com/nqhhdev/ivelox-core/internal/scraper"
)

type Normalizer struct {
	client *openai.Client
}

func NewNormalizer(apiKey string) *Normalizer {
	return &Normalizer{client: openai.NewClient(apiKey)}
}

// BuildPrompt builds the prompt sent to OpenAI.
// Exported so it can be tested without an API call.
func BuildPrompt(raw *scraper.RawExam) string {
	skillSummary := make([]string, 0, len(raw.Skills))
	for skill, s := range raw.Skills {
		sectionCount := len(s.Sections)
		qCount := 0
		for _, sec := range s.Sections {
			qCount += len(sec.Questions)
		}
		skillSummary = append(skillSummary, fmt.Sprintf("%s: %d sections, %d questions", skill, sectionCount, qCount))
	}

	rawJSON, _ := json.Marshal(raw)

	return fmt.Sprintf(`You are an IELTS exam data normalizer. Analyze the following raw scraped exam data and return a JSON object with this exact structure:

{
  "series": "Cambridge 18",
  "test_number": 1,
  "year": 2022,
  "source": "cambridge",
  "difficulty": "medium",
  "quality_score": 8.5,
  "has_reading": true,
  "has_listening": false,
  "has_writing": false,
  "has_speaking": false,
  "is_complete_exam": true,
  "duplicate_hint": "",
  "skills": {
    "reading": {
      "sections": [
        {
          "position": 1,
          "title": "Section title",
          "content": "Full passage text",
          "image_urls": [],
          "audio_urls": [],
          "questions": [
            {
              "position": 1,
              "type": "mcq",
              "prompt": "Question text",
              "options": ["A. option1", "B. option2"],
              "correct": "A",
              "explanation": "Why A is correct",
              "image_url": "",
              "audio_timestamp": 0,
              "word_limit": 0
            }
          ]
        }
      ]
    }
  }
}

Rules:
- quality_score: 0-10. Deduct points for: missing answers (-3), missing explanations (-1), incomplete passages (-2), < 3 sections for reading/listening (-2)
- has_reading/listening/writing/speaking: true only if that skill has >= 1 section with >= 1 question
- is_complete_exam: true only if at least reading OR listening has >= 30 questions
- duplicate_hint: if title/series/test_number suggests this is a known Cambridge/IDP test, put "Cambridge 18 Test 1" style string, else empty
- source: "cambridge" | "idp" | "british_council" | "mock"
- Keep all original text exactly as-is. Do not paraphrase questions or passages.

Skills found: %s

Raw data:
%s`, strings.Join(skillSummary, ", "), string(rawJSON))
}

// Normalize calls OpenAI to parse and quality-score a raw exam.
// Returns the normalized data as map[string]any and quality score.
func (n *Normalizer) Normalize(ctx context.Context, raw *scraper.RawExam) (map[string]any, float64, error) {
	prompt := BuildPrompt(raw)

	resp, err := n.client.CreateChatCompletion(ctx, openai.ChatCompletionRequest{
		Model: openai.GPT4o,
		Messages: []openai.ChatCompletionMessage{
			{Role: openai.ChatMessageRoleUser, Content: prompt},
		},
		ResponseFormat: &openai.ChatCompletionResponseFormat{
			Type: openai.ChatCompletionResponseFormatTypeJSONObject,
		},
		Temperature: 0.1,
	})
	if err != nil {
		return nil, 0, fmt.Errorf("openai normalize: %w", err)
	}

	content := resp.Choices[0].Message.Content
	var result map[string]any
	if err := json.Unmarshal([]byte(content), &result); err != nil {
		return nil, 0, fmt.Errorf("parse openai response: %w", err)
	}

	qualityScore, _ := result["quality_score"].(float64)
	return result, qualityScore, nil
}
