package gemini

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"github.com/google/generative-ai-go/genai"
	"github.com/nqhhdev/ivelox-core/internal/domain"
	"google.golang.org/api/option"
)

const (
	nutritionModel   = "gemini-2.5-flash-lite"
	nutritionTimeout = 30 * time.Second
)

const nutritionSchema = `{
  "items": [
    {
      "name": "food name",
      "quantity": 1,
      "unit": "g|ml|serving|piece",
      "kcal": 0,
      "protein_g": 0,
      "carb_g": 0,
      "fat_g": 0,
      "confidence": 0.0
    }
  ],
  "notes": "optional assumption"
}`

// NutritionClient resolves food nutrition via Gemini text and vision.
type NutritionClient struct {
	client *genai.Client
	model  string
}

func NewNutritionClient(ctx context.Context, apiKey string) (*NutritionClient, error) {
	client, err := genai.NewClient(ctx, option.WithAPIKey(apiKey))
	if err != nil {
		return nil, fmt.Errorf("gemini nutrition client: %w", err)
	}
	return &NutritionClient{client: client, model: nutritionModel}, nil
}

func (c *NutritionClient) Close() {
	if c.client != nil {
		c.client.Close()
	}
}

var _ domain.NutritionResolver = (*NutritionClient)(nil)

func (c *NutritionClient) ResolveText(ctx context.Context, text string, quantity *float64, unit *domain.FoodUnit) (*domain.ResolveResult, error) {
	return c.generate(ctx, genai.Text(buildTextPrompt(text, quantity, unit)))
}

func (c *NutritionClient) ResolveImage(ctx context.Context, imageBytes []byte, mime string, hint string) (*domain.ResolveResult, error) {
	return c.generate(ctx, imageBlob(mime, imageBytes), genai.Text(buildImagePrompt(hint)))
}

func (c *NutritionClient) generate(ctx context.Context, parts ...genai.Part) (*domain.ResolveResult, error) {
	ctx, cancel := context.WithTimeout(ctx, nutritionTimeout)
	defer cancel()

	model := c.client.GenerativeModel(c.model)
	model.SetTemperature(0.1)

	raw, err := c.generateRaw(ctx, model, parts...)
	if err != nil {
		return nil, err
	}

	result, err := ParseNutritionJSON(raw)
	if err == nil {
		result.Source = "ai"
		return result, nil
	}

	repair := fmt.Sprintf("The previous response was not valid JSON matching the nutrition schema. Fix JSON only. Return only the corrected JSON object, no markdown.\n\nPrevious response:\n%s", raw)
	raw, err = c.generateRaw(ctx, model, genai.Text(repair))
	if err != nil {
		return nil, fmt.Errorf("gemini nutrition repair: %w", err)
	}

	result, err = ParseNutritionJSON(raw)
	if err != nil {
		return nil, fmt.Errorf("gemini nutrition json: %w", err)
	}
	result.Source = "ai"
	return result, nil
}

func (c *NutritionClient) generateRaw(ctx context.Context, model *genai.GenerativeModel, parts ...genai.Part) (string, error) {
	resp, err := model.GenerateContent(ctx, parts...)
	if err != nil {
		return "", fmt.Errorf("gemini generate: %w", err)
	}
	if len(resp.Candidates) == 0 || resp.Candidates[0].Content == nil || len(resp.Candidates[0].Content.Parts) == 0 {
		return "", fmt.Errorf("gemini empty response")
	}
	return fmt.Sprintf("%v", resp.Candidates[0].Content.Parts[0]), nil
}

// ParseNutritionJSON extracts, unmarshals, and validates a nutrition resolve payload.
func ParseNutritionJSON(raw string) (*domain.ResolveResult, error) {
	raw = extractJSON(raw)
	var result domain.ResolveResult
	if err := json.Unmarshal([]byte(raw), &result); err != nil {
		return nil, fmt.Errorf("nutrition json parse: %w", err)
	}
	if err := validateResolveResult(&result); err != nil {
		return nil, err
	}
	return &result, nil
}

func validateResolveResult(r *domain.ResolveResult) error {
	if len(r.Items) == 0 {
		return fmt.Errorf("nutrition json: items required")
	}
	for i, item := range r.Items {
		if strings.TrimSpace(item.Name) == "" {
			return fmt.Errorf("nutrition json: item %d name required", i)
		}
		if item.Quantity <= 0 {
			return fmt.Errorf("nutrition json: item %d quantity must be > 0", i)
		}
		if !validFoodUnit(item.Unit) {
			return fmt.Errorf("nutrition json: item %d invalid unit %q", i, item.Unit)
		}
		if item.Kcal < 0 {
			return fmt.Errorf("nutrition json: item %d kcal must be >= 0", i)
		}
		if item.ProteinG < 0 || item.CarbG < 0 || item.FatG < 0 {
			return fmt.Errorf("nutrition json: item %d macros must be >= 0", i)
		}
		if item.Confidence < 0 || item.Confidence > 1 {
			return fmt.Errorf("nutrition json: item %d confidence must be 0-1", i)
		}
	}
	return nil
}

func validFoodUnit(u domain.FoodUnit) bool {
	switch u {
	case domain.UnitG, domain.UnitML, domain.UnitServing, domain.UnitPiece:
		return true
	default:
		return false
	}
}

func extractJSON(s string) string {
	start := strings.Index(s, "{")
	end := strings.LastIndex(s, "}")
	if start == -1 || end == -1 || end <= start {
		return s
	}
	return s[start : end+1]
}

func buildTextPrompt(text string, quantity *float64, unit *domain.FoodUnit) string {
	var extra string
	if quantity != nil || unit != nil {
		qty := "unspecified"
		if quantity != nil {
			qty = fmt.Sprintf("%g", *quantity)
		}
		u := "unspecified"
		if unit != nil {
			u = string(*unit)
		}
		extra = fmt.Sprintf("\nRequested quantity: %s %s", qty, u)
	}
	return fmt.Sprintf(`You are a nutrition estimator. Estimate calories and macros for the food described.
Vietnamese dish names are allowed.

FOOD:
%s%s

Respond in JSON only, no markdown, no explanation, matching this schema:
%s

Rules:
- kcal >= 0, protein_g/carb_g/fat_g >= 0
- confidence is between 0 and 1
- unit must be one of: g, ml, serving, piece`,
		text, extra, nutritionSchema)
}

func buildImagePrompt(hint string) string {
	hintLine := ""
	if strings.TrimSpace(hint) != "" {
		hintLine = fmt.Sprintf("\nHint from user: %s", hint)
	}
	return fmt.Sprintf(`You are a nutrition estimator. Identify food in the image and estimate calories and macros.
Vietnamese dish names are allowed.%s

Respond in JSON only, no markdown, no explanation, matching this schema:
%s

Rules:
- kcal >= 0, protein_g/carb_g/fat_g >= 0
- confidence is between 0 and 1
- unit must be one of: g, ml, serving, piece
- if the image is ambiguous or not food, still return JSON with low confidence and a notes explanation`,
		hintLine, nutritionSchema)
}

func imageBlob(mime string, data []byte) genai.Blob {
	if mime == "" {
		mime = "image/jpeg"
	}
	if !strings.Contains(mime, "/") {
		return genai.ImageData(mime, data)
	}
	return genai.Blob{MIMEType: mime, Data: data}
}
