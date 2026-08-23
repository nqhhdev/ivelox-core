package com.ivelox.core.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class NutritionJsonParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesValidPayload() throws Exception {
        String raw = """
                ```json
                {"items":[{"name":"Pho","quantity":1,"unit":"serving","kcal":450,"protein_g":20,"carb_g":50,"fat_g":10,"confidence":0.8}],"notes":"ok"}
                ```
                """;
        var result = NutritionJsonParser.parse(mapper, raw);
        assertEquals(1, result.items().size());
        assertEquals("Pho", result.items().get(0).name());
        assertEquals(450, result.items().get(0).kcal());
        assertEquals("ok", result.notes());
    }

    @Test
    void rejectsEmptyItems() {
        assertThrows(Exception.class, () -> NutritionJsonParser.parse(mapper, "{\"items\":[]}"));
    }

    @Test
    void extractJsonFindsObject() {
        assertTrue(NutritionJsonParser.extractJson("x {\"a\":1} y").contains("\"a\""));
    }
}
