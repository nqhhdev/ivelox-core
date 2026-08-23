package gemini_test

import (
	"testing"

	"github.com/nqhhdev/ivelox-core/internal/infrastructure/gemini"
)

func TestParseNutritionJSON_Valid(t *testing.T) {
	raw := `{"items":[{"name":"com tam","quantity":1,"unit":"serving","kcal":550,"protein_g":30,"carb_g":70,"fat_g":15,"confidence":0.8}],"notes":"plate"}`
	got, err := gemini.ParseNutritionJSON(raw)
	if err != nil {
		t.Fatal(err)
	}
	if len(got.Items) != 1 || got.Items[0].Kcal != 550 {
		t.Fatalf("%+v", got)
	}
}

func TestParseNutritionJSON_RejectsNegativeKcal(t *testing.T) {
	raw := `{"items":[{"name":"x","quantity":1,"unit":"g","kcal":-1,"protein_g":0,"carb_g":0,"fat_g":0,"confidence":0.5}]}`
	if _, err := gemini.ParseNutritionJSON(raw); err == nil {
		t.Fatal("expected error")
	}
}
