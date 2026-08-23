package com.ivelox.core.health;

import java.time.Instant;
import java.time.LocalDate;
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

    private final MealLogRepository meals;

    public MealService(MealLogRepository meals) {
        this.meals = meals;
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

        var meal = new HealthModels.MealLog(
                null,
                userId,
                foodCacheId,
                req.rawInput() == null ? "" : req.rawInput(),
                null,
                req.quantity(),
                unit,
                req.kcal(),
                req.proteinG(),
                req.carbG(),
                req.fatG(),
                mealType,
                loggedAt
        );
        return meals.create(meal);
    }

    public List<HealthModels.MealLog> list(String userId, LocalDate day) {
        return meals.listByUserDate(userId, day);
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
