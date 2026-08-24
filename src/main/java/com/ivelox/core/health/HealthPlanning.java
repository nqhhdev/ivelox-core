package com.ivelox.core.health;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** MET lookup + rule-based day meal plan from daily kcal target. */
public final class HealthPlanning {

    private HealthPlanning() {
    }

    private static final Map<String, Double> MET = Map.ofEntries(
            Map.entry("walking", 3.5),
            Map.entry("running", 9.8),
            Map.entry("cycling", 7.5),
            Map.entry("swimming", 8.0),
            Map.entry("gym", 5.0),
            Map.entry("yoga", 3.0),
            Map.entry("hiit", 10.0),
            Map.entry("football", 8.0),
            Map.entry("badminton", 5.5)
    );

    public static double estimateBurnKcal(String activity, int durationMin, double weightKg) {
        String key = activity == null ? "" : activity.trim().toLowerCase(Locale.ROOT);
        double met = MET.getOrDefault(key, 5.0);
        double w = weightKg > 0 ? weightKg : 70;
        // kcal = MET * weight_kg * hours
        return met * w * (durationMin / 60.0);
    }

    public static String burnSource(String activity) {
        String key = activity == null ? "" : activity.trim().toLowerCase(Locale.ROOT);
        return MET.containsKey(key) ? "met_table" : "manual";
    }

    public record MealSlot(
            String mealType,
            int targetKcal,
            double pct,
            String suggestion,
            String notes
    ) {
    }

    public static List<MealSlot> buildDayMealPlan(int dailyKcal) {
        int kcal = Math.max(1200, dailyKcal);
        // breakfast 25 / lunch 35 / dinner 30 / snack 10
        int breakfast = (int) Math.round(kcal * 0.25);
        int lunch = (int) Math.round(kcal * 0.35);
        int dinner = (int) Math.round(kcal * 0.30);
        int snack = Math.max(0, kcal - breakfast - lunch - dinner);

        List<MealSlot> slots = new ArrayList<>();
        slots.add(new MealSlot(
                "breakfast", breakfast, 0.25,
                pick(breakfast, "breakfast"),
                "Protein + complex carbs to start the day"
        ));
        slots.add(new MealSlot(
                "lunch", lunch, 0.35,
                pick(lunch, "lunch"),
                "Largest meal — lean protein + vegetables + rice/noodles"
        ));
        slots.add(new MealSlot(
                "dinner", dinner, 0.30,
                pick(dinner, "dinner"),
                "Lighter than lunch; finish 2–3h before sleep if possible"
        ));
        slots.add(new MealSlot(
                "snack", snack, 0.10,
                pick(snack, "snack"),
                "Fruit, yogurt, or a handful of nuts"
        ));
        return slots;
    }

    private static String pick(int targetKcal, String meal) {
        return switch (meal) {
            case "breakfast" -> targetKcal <= 350
                    ? "Trứng luộc (2) + bánh mì nguyên cám + chuối"
                    : "Phở gà tô nhỏ / bún thịt nạc + rau + trứng";
            case "lunch" -> targetKcal <= 500
                    ? "Cơm gạo lứt + ức gà / cá + rau luộc (đầy đủ đĩa)"
                    : "Cơm + thịt/cá nướng + canh rau + trái cây";
            case "dinner" -> targetKcal <= 450
                    ? "Salad ức gà / cá hấp + khoai lang nhỏ"
                    : "Cơm vừa + đậu phụ/thịt nạc + rau xào";
            default -> targetKcal <= 150
                    ? "Sữa chua không đường hoặc táo"
                    : "Sữa chua + ít hạt hoặc sinh tố ít đường";
        };
    }
}
