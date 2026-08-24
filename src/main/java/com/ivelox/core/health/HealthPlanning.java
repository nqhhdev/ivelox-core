package com.ivelox.core.health;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** MET lookup + rule-based day meal plan from daily kcal target. */
public final class HealthPlanning {

    private HealthPlanning() {
    }

    public static final List<String> ALL_MEAL_TYPES = List.of(
            "breakfast", "lunch", "dinner", "snack"
    );

    private static final Set<String> ALLOWED = Set.copyOf(ALL_MEAL_TYPES);

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

    /**
     * Split daily kcal evenly across selected meal types (canonical order).
     * Remainder goes to the last selected meal so the sum equals the daily target.
     * No food suggestions — kcal targets only.
     */
    public static List<MealSlot> buildDayMealPlan(int dailyKcal, List<String> mealTypes) {
        int kcal = Math.max(1200, dailyKcal);
        List<String> selected = normalizeMealTypes(mealTypes);
        int n = selected.size();
        int base = kcal / n;
        int remainder = kcal - base * n;

        List<MealSlot> slots = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int slotKcal = base + (i == n - 1 ? remainder : 0);
            double pct = Math.round((slotKcal * 1000.0 / kcal)) / 1000.0;
            slots.add(new MealSlot(selected.get(i), slotKcal, pct, "", ""));
        }
        return slots;
    }

    /** Canonical order, unique, at least one; invalid names dropped. */
    public static List<String> normalizeMealTypes(List<String> mealTypes) {
        LinkedHashSet<String> picked = new LinkedHashSet<>();
        if (mealTypes != null) {
            for (String raw : mealTypes) {
                if (raw == null) {
                    continue;
                }
                String key = raw.trim().toLowerCase(Locale.ROOT);
                if (ALLOWED.contains(key)) {
                    picked.add(key);
                }
            }
        }
        if (picked.isEmpty()) {
            return ALL_MEAL_TYPES;
        }
        List<String> ordered = new ArrayList<>();
        for (String m : ALL_MEAL_TYPES) {
            if (picked.contains(m)) {
                ordered.add(m);
            }
        }
        return ordered;
    }
}
