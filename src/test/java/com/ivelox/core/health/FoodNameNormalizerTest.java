package com.ivelox.core.health;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FoodNameNormalizerTest {

    @Test
    void stripsVietnameseDiacriticsAndCollapsesSpaces() {
        assertEquals("pho bo", FoodNameNormalizer.normalize("  Phở  Bò  "));
        assertEquals("com tam", FoodNameNormalizer.normalize("Cơm tấm"));
        assertEquals("banh mi", FoodNameNormalizer.normalize("Bánh mì"));
        assertEquals("do uong", FoodNameNormalizer.normalize("Đồ uống"));
        assertEquals("", FoodNameNormalizer.normalize("   "));
    }
}
