package com.ivelox.core.health;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class NutritionJsonParser {

    private NutritionJsonParser() {
    }

    public static HealthModels.ResolveResult parse(ObjectMapper mapper, String raw) throws Exception {
        String json = extractJson(raw);
        JsonNode root = mapper.readTree(json);
        JsonNode itemsNode = root.get("items");
        if (itemsNode == null || !itemsNode.isArray() || itemsNode.isEmpty()) {
            throw new IllegalArgumentException("nutrition json: items required");
        }
        List<HealthModels.FoodItem> items = new ArrayList<>();
        int i = 0;
        for (JsonNode n : itemsNode) {
            String name = text(n, "name");
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("nutrition json: item " + i + " name required");
            }
            double quantity = n.path("quantity").asDouble(0);
            if (quantity <= 0) {
                throw new IllegalArgumentException("nutrition json: item " + i + " quantity must be > 0");
            }
            String unit = text(n, "unit");
            if (!FoodUnit.isValid(unit)) {
                throw new IllegalArgumentException("nutrition json: item " + i + " invalid unit");
            }
            double kcal = n.path("kcal").asDouble(-1);
            if (kcal < 0) {
                throw new IllegalArgumentException("nutrition json: item " + i + " kcal must be >= 0");
            }
            double protein = n.path("protein_g").asDouble(-1);
            double carb = n.path("carb_g").asDouble(-1);
            double fat = n.path("fat_g").asDouble(-1);
            if (protein < 0 || carb < 0 || fat < 0) {
                throw new IllegalArgumentException("nutrition json: item " + i + " macros must be >= 0");
            }
            double confidence = n.path("confidence").asDouble(-1);
            if (confidence < 0 || confidence > 1) {
                throw new IllegalArgumentException("nutrition json: item " + i + " confidence must be 0-1");
            }
            items.add(new HealthModels.FoodItem(
                    name.trim(),
                    quantity,
                    FoodUnit.parse(unit).orElseThrow().value(),
                    kcal,
                    protein,
                    carb,
                    fat,
                    confidence
            ));
            i++;
        }
        String notes = text(root, "notes");
        return new HealthModels.ResolveResult(items, "ai", notes);
    }

    static String extractJson(String s) {
        if (s == null) {
            return "";
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return s;
        }
        return s.substring(start, end + 1);
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    public static final String SCHEMA = """
            {
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
            }""";

    public static String buildTextPrompt(String text, Double quantity, String unit) {
        String extra = "";
        if (quantity != null || (unit != null && !unit.isBlank())) {
            String qty = quantity == null ? "unspecified" : String.valueOf(quantity);
            String u = (unit == null || unit.isBlank()) ? "unspecified" : unit;
            extra = "\nRequested quantity: " + qty + " " + u;
        }
        return """
                You are a nutrition estimator. Estimate calories and macros for the food described.
                Vietnamese dish names are allowed.

                FOOD:
                %s%s

                Respond in JSON only, no markdown, no explanation, matching this schema:
                %s

                Rules:
                - kcal >= 0, protein_g/carb_g/fat_g >= 0
                - confidence is between 0 and 1
                - unit must be one of: g, ml, serving, piece
                """.formatted(text, extra, SCHEMA);
    }

    public static String buildImagePrompt(String hint, Double quantity, String unit) {
        String hintLine = (hint == null || hint.isBlank()) ? "" : "\nHint from user: " + hint;
        String qtyLine = "";
        if (quantity != null || (unit != null && !unit.isBlank())) {
            String qty = quantity == null ? "unspecified" : String.valueOf(quantity);
            String u = (unit == null || unit.isBlank()) ? "unspecified" : unit;
            qtyLine = "\nRequested quantity/serving actually consumed: " + qty + " " + u;
        }
        return """
                You are a nutrition estimator. Identify food in the image and estimate calories and macros.
                Vietnamese dish names are allowed.%s%s

                If the image shows a printed nutrition facts label (per 100g/100ml or per serving),
                read the exact values from the label instead of guessing generic averages for that
                food type. Scale the label's per-100g/serving values to match the requested quantity
                above (if given) or to the package's stated serving size (if visible). Prefer OCR'd
                label numbers over estimation whenever the label is legible, and set confidence high
                (>= 0.9) in that case; note in "notes" that values come from the label.
                If no label is visible or it is not legible, estimate normally with a lower confidence
                and say so in "notes".

                Respond in JSON only, no markdown, no explanation, matching this schema:
                %s

                Rules:
                - kcal >= 0, protein_g/carb_g/fat_g >= 0
                - confidence is between 0 and 1
                - unit must be one of: g, ml, serving, piece
                - if the image is ambiguous or not food, still return JSON with low confidence and a notes explanation
                """.formatted(hintLine, qtyLine, SCHEMA);
    }

    @SuppressWarnings("unchecked")
    public static String extractCandidateText(Map<String, Object> response) {
        Object candidates = response.get("candidates");
        if (!(candidates instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> cand)) {
            return null;
        }
        Object content = cand.get("content");
        if (!(content instanceof Map<?, ?> contentMap)) {
            return null;
        }
        Object parts = contentMap.get("parts");
        if (!(parts instanceof List<?> partList) || partList.isEmpty()) {
            return null;
        }
        Object part0 = partList.get(0);
        if (!(part0 instanceof Map<?, ?> partMap)) {
            return null;
        }
        Object text = partMap.get("text");
        return text == null ? null : String.valueOf(text);
    }
}
