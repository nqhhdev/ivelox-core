package com.ivelox.core.health;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class HealthModels {

    private HealthModels() {
    }

    public record FoodCache(
            UUID id,
            String normalizedName,
            String aliases,
            double defaultServingQty,
            String defaultServingUnit,
            double kcal,
            double proteinG,
            double carbG,
            double fatG,
            String source,
            double confidence,
            Instant updatedAt
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FoodItem(
            String name,
            double quantity,
            String unit,
            double kcal,
            @JsonProperty("protein_g") double proteinG,
            @JsonProperty("carb_g") double carbG,
            @JsonProperty("fat_g") double fatG,
            double confidence
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResolveResult(
            java.util.List<FoodItem> items,
            String source,
            String notes
    ) {
    }

    public record MealLog(
            UUID id,
            String userId,
            UUID foodCacheId,
            String rawInput,
            String imageUrl,
            double quantity,
            String unit,
            double kcal,
            double proteinG,
            double carbG,
            double fatG,
            String mealType,
            Instant loggedAt
    ) {
    }

    public record DayMealSummary(
            @JsonProperty("eaten_kcal") double eatenKcal,
            @JsonProperty("protein_g") double proteinG,
            @JsonProperty("carb_g") double carbG,
            @JsonProperty("fat_g") double fatG,
            @JsonProperty("meal_count") int mealCount
    ) {
    }

    public record MealLogResponse(
            String id,
            @JsonProperty("raw_input") String rawInput,
            double quantity,
            String unit,
            double kcal,
            @JsonProperty("protein_g") double proteinG,
            @JsonProperty("carb_g") double carbG,
            @JsonProperty("fat_g") double fatG,
            @JsonProperty("meal_type") String mealType,
            @JsonProperty("logged_at") String loggedAt
    ) {
        public static MealLogResponse from(MealLog m) {
            return new MealLogResponse(
                    m.id().toString(),
                    m.rawInput(),
                    m.quantity(),
                    m.unit(),
                    m.kcal(),
                    m.proteinG(),
                    m.carbG(),
                    m.fatG(),
                    m.mealType(),
                    m.loggedAt().toString()
            );
        }
    }

    public record ResolveFoodRequest(
            String text,
            Double quantity,
            String unit,
            @JsonProperty("image_base64") String imageBase64,
            @JsonProperty("image_mime") String imageMime
    ) {
    }

    public record CreateMealRequest(
            @JsonProperty("raw_input") String rawInput,
            @JsonProperty("food_cache_id") String foodCacheId,
            double quantity,
            String unit,
            double kcal,
            @JsonProperty("protein_g") double proteinG,
            @JsonProperty("carb_g") double carbG,
            @JsonProperty("fat_g") double fatG,
            @JsonProperty("meal_type") String mealType,
            @JsonProperty("logged_at") String loggedAt
    ) {
    }

    /** Internal resolve input (not API). */
    public record FoodResolveInput(
            String text,
            Double quantity,
            String unit,
            byte[] imageBytes,
            String imageMime
    ) {
        @JsonIgnore
        public boolean hasImage() {
            return imageBytes != null && imageBytes.length > 0;
        }
    }
}
