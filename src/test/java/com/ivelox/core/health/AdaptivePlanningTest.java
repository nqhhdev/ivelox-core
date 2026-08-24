package com.ivelox.core.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AdaptivePlanningTest {

    @Test
    void underEatenBreakfastRollsToLaterMeals() {
        var base = HealthPlanning.buildDayMealPlan(2000, List.of("breakfast", "lunch", "dinner"));
        var adjusted = AdaptivePlanning.adjust(
                base,
                Map.of("breakfast", 200.0),
                Map.of(),
                LocalTime.of(12, 0) // past breakfast window
        );
        assertEquals(3, adjusted.size());
        int lunch = slot(adjusted, "lunch").targetKcal();
        int dinner = slot(adjusted, "dinner").targetKcal();
        int breakfast = slot(adjusted, "breakfast").targetKcal();
        assertEquals(base.getFirst().targetKcal(), breakfast);
        assertEquals(1800, lunch + dinner);
        assertTrue(lunch + dinner > base.get(1).targetKcal() + base.get(2).targetKcal());
    }

    @Test
    void skippedMealRollsFullBaseForward() {
        var base = HealthPlanning.buildDayMealPlan(1800, List.of("lunch", "dinner"));
        var adjusted = AdaptivePlanning.adjust(
                base,
                Map.of(),
                Map.of("lunch", "skipped"),
                LocalTime.of(10, 0) // before windows; skip still closes lunch
        );
        assertEquals(base.get(0).targetKcal(), slot(adjusted, "lunch").targetKcal());
        assertEquals(1800, slot(adjusted, "dinner").targetKcal());
    }

    @Test
    void macroTargetsFromWeightAndKcal() {
        var m = BodyMath.macroTargets(70, 2000);
        assertTrue(m.proteinG() >= 60);
        assertTrue(m.fatG() >= 40);
        int kcalFromMacros = m.proteinG() * 4 + m.carbG() * 4 + m.fatG() * 9;
        assertTrue(Math.abs(kcalFromMacros - 2000) <= 20);
    }

    @Test
    void dayCloseTipsWhenProteinShort() {
        var tips = AdaptivePlanning.deficitTips(
                2000, 1500,
                140, 80,
                200, 180,
                60, 55
        );
        assertTrue(tips.stream().anyMatch(t -> t.toLowerCase().contains("protein")));
        assertTrue(tips.stream().anyMatch(t -> t.toLowerCase().contains("kcal")));
    }

    private static HealthPlanning.MealSlot slot(List<HealthPlanning.MealSlot> slots, String type) {
        return slots.stream().filter(s -> s.mealType().equals(type)).findFirst().orElseThrow();
    }
}
