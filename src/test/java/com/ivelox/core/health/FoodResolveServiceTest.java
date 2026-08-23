package com.ivelox.core.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class FoodResolveServiceTest {

    private FoodCacheRepository cache;
    private GeminiNutritionClient gemini;
    private FoodResolveService service;

    @BeforeEach
    void setUp() {
        cache = mock(FoodCacheRepository.class);
        gemini = mock(GeminiNutritionClient.class);
        service = new FoodResolveService(cache, gemini);
    }

    @Test
    void usesCacheWhenFreshAndConfident() {
        var cached = new HealthModels.FoodCache(
                UUID.randomUUID(),
                "pho bo",
                "",
                1,
                "serving",
                450,
                20,
                50,
                10,
                "ai",
                0.9,
                Instant.now()
        );
        when(cache.findByNormalizedName("pho bo")).thenReturn(Optional.of(cached));

        var result = service.resolve(new HealthModels.FoodResolveInput("Phở Bò", 1.0, "serving", null, null));

        assertEquals("cache", result.source());
        assertEquals(450, result.items().get(0).kcal());
        verify(gemini, never()).resolveText(any(), any(), any());
    }

    @Test
    void scalesCacheByQuantity() {
        var cached = new HealthModels.FoodCache(
                UUID.randomUUID(),
                "rice",
                "",
                100,
                "g",
                130,
                2,
                28,
                0.3,
                "ai",
                0.9,
                Instant.now()
        );
        when(cache.findByNormalizedName("rice")).thenReturn(Optional.of(cached));

        var result = service.resolve(new HealthModels.FoodResolveInput("rice", 200.0, "g", null, null));

        assertEquals(260, result.items().get(0).kcal(), 0.01);
    }

    @Test
    void fallsBackToAiWhenCacheMiss() {
        when(cache.findByNormalizedName("banh mi")).thenReturn(Optional.empty());
        when(gemini.isConfigured()).thenReturn(true);
        when(gemini.resolveText(eq("banh mi"), any(), any())).thenReturn(
                new HealthModels.ResolveResult(
                        List.of(new HealthModels.FoodItem("banh mi", 1, "serving", 300, 10, 40, 8, 0.7)),
                        "ai",
                        null
                )
        );

        var result = service.resolve(new HealthModels.FoodResolveInput("banh mi", null, null, null, null));

        assertEquals("ai", result.source());
        verify(cache).upsertFromItem(any(), eq("ai"));
    }

    @Test
    void imageSkipsCacheAndRequiresGemini() {
        when(gemini.isConfigured()).thenReturn(false);
        assertThrows(ResponseStatusException.class, () ->
                service.resolve(new HealthModels.FoodResolveInput("hint", null, null, new byte[]{1, 2, 3}, "image/jpeg"))
        );
        verify(cache, never()).findByNormalizedName(any());
    }

    @Test
    void emptyTextWithoutImageIsBadRequest() {
        assertThrows(ResponseStatusException.class, () ->
                service.resolve(new HealthModels.FoodResolveInput("  ", null, null, null, null))
        );
    }
}
