package com.ivelox.core.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BodyMathTest {

    @Test
    void bmiNormalRange() {
        double bmi = BodyMath.bmi(170, 65);
        assertTrue(bmi > 22 && bmi < 23);
        assertEquals("normal", BodyMath.bmiCategory(bmi));
    }

    @Test
    void reduce10PercentYieldsLowerDailyTargetThanTdee() {
        double tdee = BodyMath.tdee(80, 175, 30, "male", "moderate");
        int target = BodyMath.dailyKcalTarget(80, 175, 30, "male", "moderate", -10, 12);
        assertTrue(target < tdee);
        assertTrue(target >= 1200);
        assertEquals(72.0, 80 * 0.9, 0.01);
    }

    @Test
    void mealPlanSumsNearDailyKcal() {
        var plan = HealthPlanning.buildDayMealPlan(2000);
        int sum = plan.stream().mapToInt(HealthPlanning.MealSlot::targetKcal).sum();
        assertEquals(2000, sum);
        assertEquals(4, plan.size());
    }
}
