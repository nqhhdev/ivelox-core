package com.ivelox.core.health;

/** BMI + Mifflin–St Jeor TDEE helpers. */
public final class BodyMath {

    private BodyMath() {
    }

    public static double bmi(double heightCm, double weightKg) {
        if (heightCm <= 0 || weightKg <= 0) {
            throw new IllegalArgumentException("height and weight must be > 0");
        }
        double m = heightCm / 100.0;
        return weightKg / (m * m);
    }

    /** Mifflin–St Jeor BMR (kcal/day). sex: male|female */
    public static double bmr(double weightKg, double heightCm, int ageYears, String sex) {
        double base = 10 * weightKg + 6.25 * heightCm - 5 * ageYears;
        if ("female".equalsIgnoreCase(sex)) {
            return base - 161;
        }
        return base + 5; // male / default
    }

    public static double activityMultiplier(String level) {
        if (level == null) {
            return 1.55;
        }
        return switch (level.trim().toLowerCase()) {
            case "sedentary" -> 1.2;
            case "light" -> 1.375;
            case "moderate" -> 1.55;
            case "active" -> 1.725;
            case "very_active" -> 1.9;
            default -> 1.55;
        };
    }

    public static double tdee(double weightKg, double heightCm, int ageYears, String sex, String activity) {
        return bmr(weightKg, heightCm, ageYears, sex) * activityMultiplier(activity);
    }

    /**
     * Target daily intake for a weight change over weeks.
     * 7700 kcal ≈ 1 kg fat. Caps deficit/surplus at 750 kcal/day for safety.
     */
    public static int dailyKcalTarget(
            double weightKg,
            double heightCm,
            int ageYears,
            String sex,
            String activity,
            double weightChangePct,
            int weeks
    ) {
        double tdee = tdee(weightKg, heightCm, ageYears, sex, activity);
        double targetWeight = weightKg * (1.0 + weightChangePct / 100.0);
        double deltaKg = targetWeight - weightKg;
        int w = Math.max(weeks, 1);
        double dailyDelta = (deltaKg * 7700.0) / (w * 7.0);
        // losing weight → negative dailyDelta → subtract from TDEE
        double clamped = Math.max(-750, Math.min(750, dailyDelta));
        int target = (int) Math.round(tdee + clamped);
        // floor for safety
        return Math.max(1200, target);
    }

    public static String bmiCategory(double bmi) {
        if (bmi < 18.5) return "underweight";
        if (bmi < 25) return "normal";
        if (bmi < 30) return "overweight";
        return "obese";
    }

    public record MacroTargets(int proteinG, int carbG, int fatG) {
    }

    /**
     * Derive daily macro gram targets from body weight + kcal budget.
     * protein ≈ 1.8 g/kg, fat ≈ 0.8 g/kg, carbs fill remaining kcal.
     */
    public static MacroTargets macroTargets(double weightKg, int dailyKcal) {
        double w = weightKg > 0 ? weightKg : 70;
        int protein = (int) Math.round(clamp(1.8 * w, 60, 220));
        int fat = (int) Math.round(clamp(0.8 * w, 40, 120));
        int kcalLeft = dailyKcal - protein * 4 - fat * 9;
        int carb = Math.max(0, (int) Math.round(kcalLeft / 4.0));
        return new MacroTargets(protein, carb, fat);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
