package com.ivelox.core.health;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Time/skip-aware redistribution of daily kcal across open meal slots. */
public final class AdaptivePlanning {

    private AdaptivePlanning() {
    }

    /** ICT meal windows end (inclusive close after this time). */
    public static LocalTime windowEnd(String mealType) {
        return switch (mealType == null ? "" : mealType.toLowerCase(Locale.ROOT)) {
            case "breakfast" -> LocalTime.of(10, 30);
            case "lunch" -> LocalTime.of(14, 30);
            case "dinner" -> LocalTime.of(20, 30);
            case "snack" -> LocalTime.of(23, 0);
            default -> LocalTime.of(23, 0);
        };
    }

    public static boolean isClosed(
            String mealType,
            Map<String, String> statuses,
            LocalTime now
    ) {
        String st = statuses.getOrDefault(mealType, "planned");
        if ("done".equalsIgnoreCase(st) || "skipped".equalsIgnoreCase(st)) {
            return true;
        }
        return !now.isBefore(windowEnd(mealType));
    }

    /**
     * Closed slots keep base target. Open slots split (daily − eatenSoFar) evenly,
     * with a floor of 10% of each slot's base.
     */
    public static List<HealthPlanning.MealSlot> adjust(
            List<HealthPlanning.MealSlot> base,
            Map<String, Double> eatenByMeal,
            Map<String, String> statuses,
            LocalTime now
    ) {
        if (base == null || base.isEmpty()) {
            return List.of();
        }
        int daily = base.stream().mapToInt(HealthPlanning.MealSlot::targetKcal).sum();
        double eatenTotal = eatenByMeal == null ? 0
                : eatenByMeal.values().stream().mapToDouble(Double::doubleValue).sum();
        int remainingBudget = Math.max(0, (int) Math.round(daily - eatenTotal));

        List<HealthPlanning.MealSlot> open = new ArrayList<>();
        for (HealthPlanning.MealSlot s : base) {
            if (!isClosed(s.mealType(), statuses == null ? Map.of() : statuses, now)) {
                open.add(s);
            }
        }

        List<HealthPlanning.MealSlot> out = new ArrayList<>(base.size());
        if (open.isEmpty()) {
            for (HealthPlanning.MealSlot s : base) {
                out.add(copy(s, s.targetKcal()));
            }
            return out;
        }

        int n = open.size();
        int[] shares = new int[n];
        int baseSumOpen = open.stream().mapToInt(HealthPlanning.MealSlot::targetKcal).sum();
        int assigned = 0;
        for (int i = 0; i < n; i++) {
            HealthPlanning.MealSlot s = open.get(i);
            int floor = Math.max(1, (int) Math.round(s.targetKcal() * 0.10));
            int share;
            if (i == n - 1) {
                share = Math.max(floor, remainingBudget - assigned);
            } else if (baseSumOpen > 0) {
                share = (int) Math.round(remainingBudget * (s.targetKcal() / (double) baseSumOpen));
                share = Math.max(floor, share);
            } else {
                share = Math.max(floor, remainingBudget / n);
            }
            shares[i] = share;
            assigned += share;
        }
        // fix over-assignment from floors
        int overflow = assigned - remainingBudget;
        if (overflow > 0) {
            for (int i = n - 1; i >= 0 && overflow > 0; i--) {
                int floor = Math.max(1, (int) Math.round(open.get(i).targetKcal() * 0.10));
                int reducible = shares[i] - floor;
                if (reducible <= 0) {
                    continue;
                }
                int cut = Math.min(reducible, overflow);
                shares[i] -= cut;
                overflow -= cut;
            }
        } else if (overflow < 0) {
            shares[n - 1] += -overflow;
        }

        int oi = 0;
        for (HealthPlanning.MealSlot s : base) {
            if (isClosed(s.mealType(), statuses == null ? Map.of() : statuses, now)) {
                out.add(copy(s, s.targetKcal()));
            } else {
                int kcal = shares[oi++];
                double pct = daily > 0 ? Math.round(kcal * 1000.0 / daily) / 1000.0 : 0;
                out.add(new HealthPlanning.MealSlot(s.mealType(), kcal, pct, "", ""));
            }
        }
        return out;
    }

    public static List<String> deficitTips(
            int kcalTarget, double kcalEaten,
            int proteinTarget, double proteinEaten,
            int carbTarget, double carbEaten,
            int fatTarget, double fatEaten
    ) {
        List<String> tips = new ArrayList<>();
        double kcalGap = kcalTarget - kcalEaten;
        if (kcalGap >= 150) {
            tips.add(String.format(Locale.ROOT,
                    "Short ~%.0f kcal — add a balanced meal or snack tomorrow.", kcalGap));
        } else if (kcalGap <= -150) {
            tips.add(String.format(Locale.ROOT,
                    "Over by ~%.0f kcal — keep the next day closer to target.", -kcalGap));
        }
        double pGap = proteinTarget - proteinEaten;
        if (pGap >= 15) {
            tips.add(String.format(Locale.ROOT,
                    "Protein short ~%.0fg — eggs, Greek yogurt, chicken, tofu, or fish.", pGap));
        }
        double cGap = carbTarget - carbEaten;
        if (cGap >= 30) {
            tips.add(String.format(Locale.ROOT,
                    "Carbs short ~%.0fg — rice, noodles, fruit, or oats.", cGap));
        }
        double fGap = fatTarget - fatEaten;
        if (fGap >= 10) {
            tips.add(String.format(Locale.ROOT,
                    "Fat short ~%.0fg — nuts, avocado, olive oil, or fatty fish.", fGap));
        }
        if (tips.isEmpty()) {
            tips.add("Macros look on track — keep logging consistently.");
        }
        return tips;
    }

    private static HealthPlanning.MealSlot copy(HealthPlanning.MealSlot s, int kcal) {
        double pct = s.pct();
        return new HealthPlanning.MealSlot(s.mealType(), kcal, pct, s.suggestion(), s.notes());
    }
}
