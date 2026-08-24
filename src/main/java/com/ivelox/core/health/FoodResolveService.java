package com.ivelox.core.health;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FoodResolveService {

    static final double CACHE_MIN_CONFIDENCE = 0.6;
    static final Duration CACHE_TTL = Duration.ofDays(90);
    static final String SOURCE_CACHE = "cache";
    static final String SOURCE_AI = "ai";

    private final FoodCacheRepository cache;
    private final GeminiNutritionClient gemini;

    public FoodResolveService(FoodCacheRepository cache, GeminiNutritionClient gemini) {
        this.cache = cache;
        this.gemini = gemini;
    }

    public HealthModels.ResolveResult resolve(HealthModels.FoodResolveInput in) {
        if (in.hasImage()) {
            return resolveImage(in);
        }
        String normalized = FoodNameNormalizer.normalize(in.text());
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text or image is required");
        }
        var cached = cache.findByNormalizedName(normalized).orElse(null);
        if (useCache(cached, in.quantity(), in.unit())) {
            return resultFromCache(cached, in);
        }
        return resolveText(in);
    }

    private HealthModels.ResolveResult resolveImage(HealthModels.FoodResolveInput in) {
        requireGemini();
        HealthModels.ResolveResult result = gemini.resolveImage(in.imageBytes(), in.imageMime(), in.text(), in.quantity(), in.unit());
        upsertItems(result.items());
        return new HealthModels.ResolveResult(result.items(), SOURCE_AI, result.notes());
    }

    private HealthModels.ResolveResult resolveText(HealthModels.FoodResolveInput in) {
        requireGemini();
        HealthModels.ResolveResult result = gemini.resolveText(in.text(), in.quantity(), in.unit());
        upsertItems(result.items());
        return new HealthModels.ResolveResult(result.items(), SOURCE_AI, result.notes());
    }

    private void requireGemini() {
        if (!gemini.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "nutrition resolver unavailable");
        }
    }

    private void upsertItems(List<HealthModels.FoodItem> items) {
        for (HealthModels.FoodItem item : items) {
            cache.upsertFromItem(item, SOURCE_AI);
        }
    }

    static boolean useCache(HealthModels.FoodCache cached, Double qty, String unit) {
        if (cached == null) {
            return false;
        }
        if (cached.confidence() < CACHE_MIN_CONFIDENCE) {
            return false;
        }
        if (Duration.between(cached.updatedAt(), Instant.now()).compareTo(CACHE_TTL) >= 0) {
            return false;
        }
        if (unit != null && !unit.isBlank() && !unit.equalsIgnoreCase(cached.defaultServingUnit())) {
            return false;
        }
        if (qty != null && cached.defaultServingQty() == 0) {
            return false;
        }
        return true;
    }

    static HealthModels.ResolveResult resultFromCache(HealthModels.FoodCache cached, HealthModels.FoodResolveInput in) {
        double factor = 1.0;
        if (in.quantity() != null && cached.defaultServingQty() != 0) {
            factor = in.quantity() / cached.defaultServingQty();
        }
        double qty = in.quantity() != null ? in.quantity() : cached.defaultServingQty();
        String unit = (in.unit() != null && !in.unit().isBlank()) ? in.unit() : cached.defaultServingUnit();
        String name = in.text() == null ? "" : in.text().trim();
        if (name.isEmpty()) {
            name = cached.normalizedName();
        }
        var item = new HealthModels.FoodItem(
                name,
                qty,
                unit,
                cached.kcal() * factor,
                cached.proteinG() * factor,
                cached.carbG() * factor,
                cached.fatG() * factor,
                cached.confidence()
        );
        return new HealthModels.ResolveResult(List.of(item), SOURCE_CACHE, null);
    }
}
