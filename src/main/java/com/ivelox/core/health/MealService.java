package com.ivelox.core.health;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MealService {

    private static final Set<String> MEAL_TYPES = Set.of("breakfast", "lunch", "dinner", "snack");
    private static final int MAX_IMAGE_BYTES = 3 << 20;
    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private final MealLogRepository meals;
    private final MealImageRepository images;

    public MealService(MealLogRepository meals, MealImageRepository images) {
        this.meals = meals;
        this.images = images;
    }

    public HealthModels.MealLog create(String userId, HealthModels.CreateMealRequest req) {
        if (req.quantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be > 0");
        }
        if (req.kcal() < 0 || req.proteinG() < 0 || req.carbG() < 0 || req.fatG() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "macros must be >= 0");
        }
        String unit = FoodUnit.parse(req.unit())
                .map(FoodUnit::value)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid unit"));

        String mealType = null;
        if (req.mealType() != null && !req.mealType().isBlank()) {
            mealType = req.mealType().trim().toLowerCase(Locale.ROOT);
            if (!MEAL_TYPES.contains(mealType)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid meal_type");
            }
        }

        UUID foodCacheId = null;
        if (req.foodCacheId() != null && !req.foodCacheId().isBlank()) {
            try {
                foodCacheId = UUID.fromString(req.foodCacheId().trim());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid food_cache_id");
            }
        }

        Instant loggedAt = Instant.now();
        if (req.loggedAt() != null && !req.loggedAt().isBlank()) {
            try {
                loggedAt = Instant.parse(req.loggedAt().trim());
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "logged_at must be RFC3339");
            }
        }

        byte[] imageBytes = null;
        String mime = null;
        if (req.imageBase64() != null && !req.imageBase64().isBlank()) {
            try {
                imageBytes = Base64.getDecoder().decode(req.imageBase64().trim());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid image_base64");
            }
            mime = req.imageMime() == null ? "image/jpeg" : req.imageMime().trim().toLowerCase(Locale.ROOT);
            if (imageBytes.length == 0 || imageBytes.length > MAX_IMAGE_BYTES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image too large");
            }
            if (!ALLOWED_MIME.contains(mime)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported image_mime");
            }
        }

        UUID id = UUID.randomUUID();
        String imageUrl = imageBytes != null ? "/api/v1/health/meals/" + id + "/image" : null;

        var meal = new HealthModels.MealLog(
                id,
                userId,
                foodCacheId,
                req.rawInput() == null ? "" : req.rawInput(),
                imageUrl,
                req.quantity(),
                unit,
                req.kcal(),
                req.proteinG(),
                req.carbG(),
                req.fatG(),
                mealType,
                loggedAt
        );
        var saved = meals.create(meal);
        if (imageBytes != null) {
            images.upsert(new MealImageRepository.MealImage(saved.id(), userId, mime, imageBytes));
        }
        return saved;
    }

    public List<HealthModels.MealLog> list(String userId, LocalDate day) {
        return meals.listByUserDate(userId, day);
    }

    public HealthModels.MealLogResponse toResponse(String userId, HealthModels.MealLog meal) {
        boolean hasImg = meal.imageUrl() != null && !meal.imageUrl().isBlank()
                || images.exists(userId, meal.id());
        return HealthModels.MealLogResponse.from(meal, hasImg);
    }

    public MealImageRepository.MealImage getImage(String userId, UUID mealId) {
        return images.find(userId, mealId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "image not found"));
    }

    public void delete(String userId, UUID id) {
        if (!meals.delete(userId, id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");
        }
    }

    public HealthModels.DayMealSummary todaySummary(String userId, LocalDate day) {
        return meals.summarizeDay(userId, day);
    }
}
