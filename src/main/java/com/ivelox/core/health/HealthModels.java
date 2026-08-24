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
            @JsonProperty("logged_at") String loggedAt,
            @JsonProperty("has_image") boolean hasImage,
            @JsonProperty("image_url") String imageUrl
    ) {
        public static MealLogResponse from(MealLog m, boolean hasImage) {
            String url = hasImage && m.id() != null
                    ? "/api/v1/health/meals/" + m.id() + "/image"
                    : m.imageUrl();
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
                    m.loggedAt().toString(),
                    hasImage || (m.imageUrl() != null && !m.imageUrl().isBlank()),
                    url
            );
        }

        public static MealLogResponse from(MealLog m) {
            return from(m, m.imageUrl() != null && !m.imageUrl().isBlank());
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
            @JsonProperty("logged_at") String loggedAt,
            @JsonProperty("image_base64") String imageBase64,
            @JsonProperty("image_mime") String imageMime
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

    public record BodyMetric(
            UUID id,
            String userId,
            @JsonProperty("height_cm") double heightCm,
            @JsonProperty("weight_kg") double weightKg,
            double bmi,
            @JsonProperty("recorded_at") Instant recordedAt
    ) {
    }

    public record BodyMetricResponse(
            String id,
            @JsonProperty("height_cm") double heightCm,
            @JsonProperty("weight_kg") double weightKg,
            double bmi,
            @JsonProperty("bmi_category") String bmiCategory,
            @JsonProperty("recorded_at") String recordedAt
    ) {
        public static BodyMetricResponse from(BodyMetric m) {
            return new BodyMetricResponse(
                    m.id().toString(),
                    m.heightCm(),
                    m.weightKg(),
                    round1(m.bmi()),
                    BodyMath.bmiCategory(m.bmi()),
                    m.recordedAt().toString()
            );
        }
    }

    public record CreateBodyMetricRequest(
            @JsonProperty("height_cm") double heightCm,
            @JsonProperty("weight_kg") double weightKg,
            @JsonProperty("recorded_at") String recordedAt
    ) {
    }

    public record BurnLog(
            UUID id,
            String userId,
            String activityName,
            int durationMin,
            double kcalBurned,
            String source,
            Instant loggedAt
    ) {
    }

    public record BurnLogResponse(
            String id,
            @JsonProperty("activity_name") String activityName,
            @JsonProperty("duration_min") int durationMin,
            @JsonProperty("kcal_burned") double kcalBurned,
            String source,
            @JsonProperty("logged_at") String loggedAt
    ) {
        public static BurnLogResponse from(BurnLog b) {
            return new BurnLogResponse(
                    b.id().toString(),
                    b.activityName(),
                    b.durationMin(),
                    round1(b.kcalBurned()),
                    b.source(),
                    b.loggedAt().toString()
            );
        }
    }

    public record CreateBurnRequest(
            @JsonProperty("activity_name") String activityName,
            @JsonProperty("duration_min") int durationMin,
            @JsonProperty("kcal_burned") Double kcalBurned,
            @JsonProperty("logged_at") String loggedAt
    ) {
    }

    public record HealthGoal(
            String userId,
            Double heightCm,
            Double weightKg,
            String sex,
            Integer ageYears,
            String activityLevel,
            Double weightChangePct,
            Integer weeks,
            Double targetWeightKg,
            Integer dailyKcalTarget,
            Integer dailyBurnTarget,
            java.time.LocalDate startAt,
            java.time.LocalDate targetAt,
            String mealPlanJson,
            Integer proteinGTarget,
            Integer carbGTarget,
            Integer fatGTarget,
            String mealTypesJson,
            Instant updatedAt
    ) {
    }

    public record UpsertGoalRequest(
            @JsonProperty("height_cm") Double heightCm,
            @JsonProperty("weight_kg") Double weightKg,
            String sex,
            @JsonProperty("age_years") Integer ageYears,
            @JsonProperty("activity_level") String activityLevel,
            @JsonProperty("weight_change_pct") Double weightChangePct,
            Integer weeks,
            @JsonProperty("daily_burn_target") Integer dailyBurnTarget,
            /** Selected meal slots (breakfast/lunch/dinner/snack); empty → all four. */
            @JsonProperty("meal_types") java.util.List<String> mealTypes
    ) {
    }

    public record MealPlanSlotResponse(
            @JsonProperty("meal_type") String mealType,
            @JsonProperty("target_kcal") int targetKcal,
            double pct,
            String suggestion,
            String notes,
            String status,
            @JsonProperty("eaten_kcal") Double eatenKcal,
            @JsonProperty("base_kcal") Integer baseKcal
    ) {
        public MealPlanSlotResponse(String mealType, int targetKcal, double pct, String suggestion, String notes) {
            this(mealType, targetKcal, pct, suggestion, notes, null, null, null);
        }
    }

    public record GoalResponse(
            @JsonProperty("height_cm") Double heightCm,
            @JsonProperty("weight_kg") Double weightKg,
            Double bmi,
            @JsonProperty("bmi_category") String bmiCategory,
            String sex,
            @JsonProperty("age_years") Integer ageYears,
            @JsonProperty("activity_level") String activityLevel,
            @JsonProperty("weight_change_pct") Double weightChangePct,
            Integer weeks,
            @JsonProperty("target_weight_kg") Double targetWeightKg,
            @JsonProperty("kg_to_change") Double kgToChange,
            @JsonProperty("daily_kcal_target") Integer dailyKcalTarget,
            @JsonProperty("daily_burn_target") Integer dailyBurnTarget,
            @JsonProperty("protein_g_target") Integer proteinGTarget,
            @JsonProperty("carb_g_target") Integer carbGTarget,
            @JsonProperty("fat_g_target") Integer fatGTarget,
            @JsonProperty("start_at") String startAt,
            @JsonProperty("target_at") String targetAt,
            @JsonProperty("meal_plan") java.util.List<MealPlanSlotResponse> mealPlan,
            @JsonProperty("meal_types") java.util.List<String> mealTypes
    ) {
    }

    public record TodayCheckResponse(
            @JsonProperty("eaten_kcal") double eatenKcal,
            @JsonProperty("burned_kcal") double burnedKcal,
            @JsonProperty("net_kcal") double netKcal,
            @JsonProperty("remaining_kcal") Double remainingKcal,
            @JsonProperty("daily_kcal_target") Integer dailyKcalTarget,
            @JsonProperty("protein_g") double proteinG,
            @JsonProperty("carb_g") double carbG,
            @JsonProperty("fat_g") double fatG,
            @JsonProperty("protein_g_target") Integer proteinGTarget,
            @JsonProperty("carb_g_target") Integer carbGTarget,
            @JsonProperty("fat_g_target") Integer fatGTarget,
            @JsonProperty("meal_count") int mealCount,
            Double bmi,
            @JsonProperty("bmi_category") String bmiCategory,
            @JsonProperty("target_weight_kg") Double targetWeightKg,
            @JsonProperty("weight_kg_today") Double weightKgToday,
            String tip,
            @JsonProperty("meal_plan") java.util.List<MealPlanSlotResponse> mealPlan,
            @JsonProperty("day_closed") boolean dayClosed,
            @JsonProperty("deficit_tips") java.util.List<String> deficitTips
    ) {
    }

    public record WeeklyCheckResponse(
            int days,
            @JsonProperty("eaten_kcal") double eatenKcal,
            @JsonProperty("burned_kcal") double burnedKcal,
            @JsonProperty("net_kcal") double netKcal,
            @JsonProperty("avg_eaten_kcal") double avgEatenKcal,
            @JsonProperty("daily_kcal_target") Integer dailyKcalTarget,
            Integer score,
            java.util.List<String> tips
    ) {
    }

    public record UpsertMealSlotRequest(
            @JsonProperty("date") String date,
            @JsonProperty("meal_type") String mealType,
            String status
    ) {
    }

    public record DailyWeightRequest(
            @JsonProperty("date") String date,
            @JsonProperty("weight_kg") double weightKg
    ) {
    }

    public record DailyWeightResponse(
            @JsonProperty("date") String date,
            @JsonProperty("weight_kg") double weightKg,
            Double bmi,
            @JsonProperty("bmi_category") String bmiCategory
    ) {
    }

    public record DayCloseResponse(
            @JsonProperty("date") String date,
            @JsonProperty("eaten_kcal") double eatenKcal,
            @JsonProperty("burned_kcal") double burnedKcal,
            @JsonProperty("net_kcal") double netKcal,
            @JsonProperty("protein_g") double proteinG,
            @JsonProperty("carb_g") double carbG,
            @JsonProperty("fat_g") double fatG,
            @JsonProperty("kcal_target") Integer kcalTarget,
            @JsonProperty("protein_g_target") Integer proteinGTarget,
            @JsonProperty("carb_g_target") Integer carbGTarget,
            @JsonProperty("fat_g_target") Integer fatGTarget,
            java.util.List<String> tips
    ) {
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
