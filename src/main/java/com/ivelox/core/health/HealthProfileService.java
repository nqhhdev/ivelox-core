package com.ivelox.core.health;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class HealthProfileService {

    private static final Set<String> SEX = Set.of("male", "female");
    private static final Set<String> ACTIVITY = Set.of("sedentary", "light", "moderate", "active", "very_active");
    private static final Set<String> SLOT_STATUS = Set.of("planned", "done", "skipped");
    private static final Set<String> MEAL_TYPES = Set.of("breakfast", "lunch", "dinner", "snack");

    private final BodyMetricsRepository bodyRepo;
    private final BurnLogRepository burnRepo;
    private final HealthGoalsRepository goalsRepo;
    private final MealLogRepository mealRepo;
    private final MealSlotStateRepository slotRepo;
    private final DailyWeightRepository weightRepo;
    private final DayClosingRepository closingRepo;
    private final ObjectMapper json;

    public HealthProfileService(
            BodyMetricsRepository bodyRepo,
            BurnLogRepository burnRepo,
            HealthGoalsRepository goalsRepo,
            MealLogRepository mealRepo,
            MealSlotStateRepository slotRepo,
            DailyWeightRepository weightRepo,
            DayClosingRepository closingRepo,
            ObjectMapper json
    ) {
        this.bodyRepo = bodyRepo;
        this.burnRepo = burnRepo;
        this.goalsRepo = goalsRepo;
        this.mealRepo = mealRepo;
        this.slotRepo = slotRepo;
        this.weightRepo = weightRepo;
        this.closingRepo = closingRepo;
        this.json = json;
    }

    public HealthModels.BodyMetricResponse recordBody(String userId, HealthModels.CreateBodyMetricRequest req) {
        if (req.heightCm() <= 0 || req.weightKg() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "height_cm and weight_kg must be > 0");
        }
        Instant recorded = Instant.now();
        if (req.recordedAt() != null && !req.recordedAt().isBlank()) {
            try {
                recorded = Instant.parse(req.recordedAt().trim());
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recorded_at must be RFC3339");
            }
        }
        double bmi = BodyMath.bmi(req.heightCm(), req.weightKg());
        var saved = bodyRepo.create(new HealthModels.BodyMetric(
                null, userId, req.heightCm(), req.weightKg(), bmi, recorded
        ));
        return HealthModels.BodyMetricResponse.from(saved);
    }

    public HealthModels.BodyMetricResponse latestBody(String userId) {
        return bodyRepo.latest(userId)
                .map(HealthModels.BodyMetricResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no body metrics yet"));
    }

    public List<HealthModels.BodyMetricResponse> bodyHistory(String userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "to must be >= from");
        }
        return bodyRepo.history(userId, from, to).stream()
                .map(HealthModels.BodyMetricResponse::from)
                .toList();
    }

    public HealthModels.DailyWeightResponse upsertDailyWeight(String userId, HealthModels.DailyWeightRequest req) {
        if (req.weightKg() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "weight_kg must be > 0");
        }
        LocalDate day = req.date() == null || req.date().isBlank()
                ? CivilDay.todayIct()
                : CivilDay.parse(req.date());
        weightRepo.upsert(userId, day, req.weightKg());
        double height = bodyRepo.latest(userId).map(HealthModels.BodyMetric::heightCm).orElse(0.0);
        Double bmi = null;
        String cat = null;
        if (height > 0) {
            bmi = Math.round(BodyMath.bmi(height, req.weightKg()) * 10.0) / 10.0;
            cat = BodyMath.bmiCategory(bmi);
            bodyRepo.create(new HealthModels.BodyMetric(null, userId, height, req.weightKg(), bmi, Instant.now()));
        }
        return new HealthModels.DailyWeightResponse(day.toString(), req.weightKg(), bmi, cat);
    }

    public HealthModels.BurnLogResponse createBurn(String userId, HealthModels.CreateBurnRequest req) {
        if (req.activityName() == null || req.activityName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "activity_name required");
        }
        if (req.durationMin() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "duration_min must be > 0");
        }
        double weight = bodyRepo.latest(userId).map(HealthModels.BodyMetric::weightKg).orElse(70.0);
        double kcal = req.kcalBurned() != null && req.kcalBurned() > 0
                ? req.kcalBurned()
                : HealthPlanning.estimateBurnKcal(req.activityName(), req.durationMin(), weight);
        String source = req.kcalBurned() != null && req.kcalBurned() > 0
                ? "manual"
                : HealthPlanning.burnSource(req.activityName());

        Instant logged = Instant.now();
        if (req.loggedAt() != null && !req.loggedAt().isBlank()) {
            try {
                logged = Instant.parse(req.loggedAt().trim());
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "logged_at must be RFC3339");
            }
        }

        var saved = burnRepo.create(new HealthModels.BurnLog(
                null, userId, req.activityName().trim(), req.durationMin(), kcal, source, logged
        ));
        return HealthModels.BurnLogResponse.from(saved);
    }

    public List<HealthModels.BurnLogResponse> listBurns(String userId, LocalDate day) {
        return burnRepo.listByUserDate(userId, day).stream()
                .map(HealthModels.BurnLogResponse::from)
                .toList();
    }

    public void deleteBurn(String userId, UUID id) {
        if (!burnRepo.delete(userId, id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");
        }
    }

    public HealthModels.GoalResponse upsertGoal(String userId, HealthModels.UpsertGoalRequest req) {
        var latest = bodyRepo.latest(userId);
        double height = req.heightCm() != null ? req.heightCm()
                : latest.map(HealthModels.BodyMetric::heightCm).orElse(0.0);
        double weight = req.weightKg() != null ? req.weightKg()
                : latest.map(HealthModels.BodyMetric::weightKg).orElse(0.0);
        if (height <= 0 || weight <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "height_cm and weight_kg required (record body metrics first or send in goal)");
        }

        String sex = req.sex() == null ? "male" : req.sex().trim().toLowerCase(Locale.ROOT);
        if (!SEX.contains(sex)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sex must be male or female");
        }
        int age = req.ageYears() == null ? 30 : req.ageYears();
        if (age < 14 || age > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "age_years out of range");
        }
        String activity = req.activityLevel() == null ? "moderate" : req.activityLevel().trim().toLowerCase(Locale.ROOT);
        if (!ACTIVITY.contains(activity)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid activity_level");
        }
        double changePct = req.weightChangePct() == null ? -10.0 : req.weightChangePct();
        if (changePct < -25 || changePct > 25) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "weight_change_pct must be between -25 and 25");
        }
        int weeks = req.weeks() == null ? 12 : req.weeks();
        if (weeks < 4 || weeks > 52) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "weeks must be 4–52");
        }

        double bmi = BodyMath.bmi(height, weight);
        double targetWeight = weight * (1.0 + changePct / 100.0);
        int dailyKcal = BodyMath.dailyKcalTarget(weight, height, age, sex, activity, changePct, weeks);
        int burnTarget = req.dailyBurnTarget() == null ? 300 : req.dailyBurnTarget();
        var macros = BodyMath.macroTargets(weight, dailyKcal);

        LocalDate start = CivilDay.todayIct();
        LocalDate targetAt = start.plusWeeks(weeks);
        var mealTypes = HealthPlanning.normalizeMealTypes(req.mealTypes());
        var slots = HealthPlanning.buildDayMealPlan(dailyKcal, mealTypes);
        String planJson;
        String mealTypesJson;
        try {
            planJson = json.writeValueAsString(slots.stream()
                    .map(s -> new HealthModels.MealPlanSlotResponse(
                            s.mealType(), s.targetKcal(), s.pct(), s.suggestion(), s.notes()))
                    .toList());
            mealTypesJson = json.writeValueAsString(mealTypes);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to store meal plan");
        }

        if (req.heightCm() != null && req.weightKg() != null) {
            bodyRepo.create(new HealthModels.BodyMetric(null, userId, height, weight, bmi, Instant.now()));
        }

        var goal = new HealthModels.HealthGoal(
                userId, height, weight, sex, age, activity, changePct, weeks,
                targetWeight, dailyKcal, burnTarget, start, targetAt, planJson,
                macros.proteinG(), macros.carbG(), macros.fatG(), mealTypesJson, Instant.now()
        );
        goalsRepo.upsert(goal);
        return toGoalResponse(goal, bmi);
    }

    public HealthModels.GoalResponse getGoal(String userId) {
        var goal = goalsRepo.find(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no goals yet"));
        double bmi = goal.heightCm() != null && goal.weightKg() != null
                ? BodyMath.bmi(goal.heightCm(), goal.weightKg())
                : bodyRepo.latest(userId).map(HealthModels.BodyMetric::bmi).orElse(0.0);
        return toGoalResponse(goal, bmi);
    }

    public List<HealthModels.MealPlanSlotResponse> mealPlan(String userId) {
        return todayCheck(userId, CivilDay.todayIct()).mealPlan();
    }

    public void setMealSlotStatus(String userId, HealthModels.UpsertMealSlotRequest req) {
        if (req.mealType() == null || !MEAL_TYPES.contains(req.mealType().toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid meal_type");
        }
        String status = req.status() == null ? "" : req.status().trim().toLowerCase(Locale.ROOT);
        if (!SLOT_STATUS.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be planned|done|skipped");
        }
        LocalDate day = req.date() == null || req.date().isBlank()
                ? CivilDay.todayIct()
                : CivilDay.parse(req.date());
        slotRepo.upsert(userId, day, req.mealType().toLowerCase(Locale.ROOT), status, null, null);
    }

    public HealthModels.TodayCheckResponse todayCheck(String userId, LocalDate day) {
        var meals = mealRepo.summarizeDay(userId, day);
        double burned = burnRepo.sumKcal(userId, day);
        double net = meals.eatenKcal() - burned;
        var goalOpt = goalsRepo.find(userId);
        Integer target = goalOpt.map(HealthModels.HealthGoal::dailyKcalTarget).orElse(null);
        Double remaining = target == null ? null : target - meals.eatenKcal();
        var body = bodyRepo.latest(userId);
        Double bmi = body.map(HealthModels.BodyMetric::bmi).orElse(null);
        String cat = bmi == null ? null : BodyMath.bmiCategory(bmi);
        Double targetWeight = goalOpt.map(HealthModels.HealthGoal::targetWeightKg).orElse(null);
        Double weightToday = weightRepo.find(userId, day)
                .or(() -> body.map(HealthModels.BodyMetric::weightKg))
                .orElse(null);

        Integer pT = goalOpt.map(HealthModels.HealthGoal::proteinGTarget).orElse(null);
        Integer cT = goalOpt.map(HealthModels.HealthGoal::carbGTarget).orElse(null);
        Integer fT = goalOpt.map(HealthModels.HealthGoal::fatGTarget).orElse(null);

        List<HealthModels.MealPlanSlotResponse> plan = adaptivePlan(userId, day, goalOpt.orElse(null));
        var closing = closingRepo.find(userId, day);
        List<String> deficitTips = closing.map(c -> parseTips(c.tipsJson())).orElse(List.of());
        String tip = closing.isPresent() && !deficitTips.isEmpty()
                ? deficitTips.getFirst()
                : buildTip(meals.eatenKcal(), burned, remaining, target);

        return new HealthModels.TodayCheckResponse(
                meals.eatenKcal(),
                burned,
                net,
                remaining,
                target,
                meals.proteinG(),
                meals.carbG(),
                meals.fatG(),
                pT,
                cT,
                fT,
                meals.mealCount(),
                bmi == null ? null : Math.round(bmi * 10.0) / 10.0,
                cat,
                targetWeight == null ? null : Math.round(targetWeight * 10.0) / 10.0,
                weightToday == null ? null : Math.round(weightToday * 10.0) / 10.0,
                tip,
                plan,
                closing.isPresent(),
                deficitTips
        );
    }

    public HealthModels.DayCloseResponse closeDay(String userId, LocalDate day) {
        var meals = mealRepo.summarizeDay(userId, day);
        double burned = burnRepo.sumKcal(userId, day);
        double net = meals.eatenKcal() - burned;
        var goal = goalsRepo.find(userId).orElse(null);
        Integer kcalT = goal == null ? null : goal.dailyKcalTarget();
        Integer pT = goal == null ? null : goal.proteinGTarget();
        Integer cT = goal == null ? null : goal.carbGTarget();
        Integer fT = goal == null ? null : goal.fatGTarget();
        if (pT == null && goal != null && goal.weightKg() != null && kcalT != null) {
            var m = BodyMath.macroTargets(goal.weightKg(), kcalT);
            pT = m.proteinG();
            cT = m.carbG();
            fT = m.fatG();
        }
        List<String> tips = AdaptivePlanning.deficitTips(
                kcalT == null ? 0 : kcalT, meals.eatenKcal(),
                pT == null ? 0 : pT, meals.proteinG(),
                cT == null ? 0 : cT, meals.carbG(),
                fT == null ? 0 : fT, meals.fatG()
        );
        String tipsJson;
        try {
            tipsJson = json.writeValueAsString(tips);
        } catch (JsonProcessingException e) {
            tipsJson = "[]";
        }
        closingRepo.upsert(new DayClosingRepository.DayClosing(
                userId, day, meals.eatenKcal(), burned, net,
                meals.proteinG(), meals.carbG(), meals.fatG(),
                kcalT, pT, cT, fT, tipsJson, Instant.now()
        ));
        return new HealthModels.DayCloseResponse(
                day.toString(), meals.eatenKcal(), burned, net,
                meals.proteinG(), meals.carbG(), meals.fatG(),
                kcalT, pT, cT, fT, tips
        );
    }

    public HealthModels.WeeklyCheckResponse weeklyCheck(String userId, int days) {
        int d = Math.min(30, Math.max(7, days));
        LocalDate end = CivilDay.todayIct();
        LocalDate start = end.minusDays(d - 1L);
        double eaten = mealRepo.sumKcalRange(userId, start, end);
        double burned = burnRepo.sumKcalRange(userId, start, end);
        double net = eaten - burned;
        double avg = eaten / d;
        Integer target = goalsRepo.find(userId).map(HealthModels.HealthGoal::dailyKcalTarget).orElse(null);

        int score = 70;
        List<String> tips = new ArrayList<>();
        if (target != null) {
            double diff = Math.abs(avg - target);
            if (diff <= target * 0.1) {
                score = 90;
                tips.add("Average intake is close to your daily target — keep the consistency.");
            } else if (avg > target) {
                score = 55;
                tips.add("Average intake is above target. Trim snacks or reduce dinner portions.");
            } else {
                score = 75;
                tips.add("Average intake is under target — ensure protein stays high enough.");
            }
        } else {
            tips.add("Set a BMI-based goal to unlock calorie targets and a daily meal plan.");
        }
        if (burned < 500) {
            tips.add("Log a few walks or gym sessions this week to raise weekly burn.");
            score = Math.max(40, score - 10);
        } else {
            tips.add("Solid activity volume this week — maintain recovery and sleep.");
        }
        if (tips.size() > 3) {
            tips = tips.subList(0, 3);
        }

        return new HealthModels.WeeklyCheckResponse(
                d, eaten, burned, net, Math.round(avg * 10.0) / 10.0, target, score, tips
        );
    }

    private List<HealthModels.MealPlanSlotResponse> adaptivePlan(
            String userId, LocalDate day, HealthModels.HealthGoal goal
    ) {
        if (goal == null) {
            return List.of();
        }
        List<HealthPlanning.MealSlot> base = parsePlan(goal.mealPlanJson()).stream()
                .map(s -> new HealthPlanning.MealSlot(
                        s.mealType(), s.targetKcal(), s.pct(),
                        s.suggestion() == null ? "" : s.suggestion(),
                        s.notes() == null ? "" : s.notes()))
                .toList();
        if (base.isEmpty() && goal.dailyKcalTarget() != null) {
            base = HealthPlanning.buildDayMealPlan(goal.dailyKcalTarget(), parseMealTypes(goal.mealTypesJson()));
        }
        Map<String, Double> eaten = mealRepo.sumKcalByMealType(userId, day);
        Map<String, String> statuses = slotRepo.statusesForDay(userId, day);
        var adjusted = AdaptivePlanning.adjust(base, eaten, statuses, CivilDay.nowIctTime());
        List<HealthModels.MealPlanSlotResponse> out = new ArrayList<>();
        for (int i = 0; i < adjusted.size(); i++) {
            var a = adjusted.get(i);
            var b = base.get(i);
            boolean closed = AdaptivePlanning.isClosed(a.mealType(), statuses, CivilDay.nowIctTime());
            String status = statuses.getOrDefault(a.mealType(), closed ? "closed" : "open");
            if ("planned".equals(status) && closed) {
                status = "closed";
            }
            Double eatenK = eaten.getOrDefault(a.mealType(), 0.0);
            out.add(new HealthModels.MealPlanSlotResponse(
                    a.mealType(), a.targetKcal(), a.pct(), "", "",
                    status, eatenK, b.targetKcal()
            ));
        }
        return out;
    }

    private HealthModels.GoalResponse toGoalResponse(HealthModels.HealthGoal goal, double bmi) {
        Double kgChange = goal.weightKg() != null && goal.targetWeightKg() != null
                ? Math.round((goal.targetWeightKg() - goal.weightKg()) * 10.0) / 10.0
                : null;
        return new HealthModels.GoalResponse(
                goal.heightCm(),
                goal.weightKg(),
                Math.round(bmi * 10.0) / 10.0,
                BodyMath.bmiCategory(bmi),
                goal.sex(),
                goal.ageYears(),
                goal.activityLevel(),
                goal.weightChangePct(),
                goal.weeks(),
                goal.targetWeightKg() == null ? null : Math.round(goal.targetWeightKg() * 10.0) / 10.0,
                kgChange,
                goal.dailyKcalTarget(),
                goal.dailyBurnTarget(),
                goal.proteinGTarget(),
                goal.carbGTarget(),
                goal.fatGTarget(),
                goal.startAt() == null ? null : goal.startAt().toString(),
                goal.targetAt() == null ? null : goal.targetAt().toString(),
                parsePlan(goal.mealPlanJson()),
                parseMealTypes(goal.mealTypesJson())
        );
    }

    private List<HealthModels.MealPlanSlotResponse> parsePlan(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(raw, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private List<String> parseMealTypes(String raw) {
        if (raw == null || raw.isBlank()) {
            return HealthPlanning.ALL_MEAL_TYPES;
        }
        try {
            List<String> list = json.readValue(raw, new TypeReference<>() {
            });
            return HealthPlanning.normalizeMealTypes(list);
        } catch (JsonProcessingException e) {
            return HealthPlanning.ALL_MEAL_TYPES;
        }
    }

    private List<String> parseTips(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(raw, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private static String buildTip(double eaten, double burned, Double remaining, Integer target) {
        if (target == null) {
            return "Set a weight goal to get a daily kcal target and meal plan.";
        }
        if (remaining != null && remaining < 0) {
            return "Over target today — prefer a lighter dinner and a short walk.";
        }
        if (eaten == 0) {
            return "No meals logged yet — stay under your daily kcal target.";
        }
        if (burned == 0) {
            return "Log activity to improve net kcal for the day.";
        }
        return "On track — keep protein steady across meals.";
    }
}
