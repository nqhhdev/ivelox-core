package com.ivelox.core.health;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.ivelox.core.config.IveloxProperties;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private static final int MAX_IMAGE_BYTES = 3 << 20;
    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private final IveloxProperties props;
    private final FoodResolveService foodResolve;
    private final MealService mealService;
    private final HealthProfileService profile;

    public HealthController(
            IveloxProperties props,
            FoodResolveService foodResolve,
            MealService mealService,
            HealthProfileService profile
    ) {
        this.props = props;
        this.foodResolve = foodResolve;
        this.mealService = mealService;
        this.profile = profile;
    }

    @PostMapping("/foods/resolve")
    public HealthModels.ResolveResult resolveFood(
            Authentication auth,
            @RequestBody HealthModels.ResolveFoodRequest req
    ) {
        requireFeature();
        ownerId(auth);

        byte[] imageBytes = null;
        String mime = null;
        if (req.imageBase64() != null && !req.imageBase64().isBlank()) {
            try {
                imageBytes = Base64.getDecoder().decode(req.imageBase64().trim());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid image_base64");
            }
            mime = req.imageMime() == null ? "" : req.imageMime();
            validateImage(imageBytes, mime);
        }
        if (req.unit() != null && !req.unit().isBlank() && FoodUnit.parse(req.unit()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid unit");
        }

        var in = new HealthModels.FoodResolveInput(
                req.text(),
                req.quantity(),
                req.unit(),
                imageBytes,
                mime
        );
        return foodResolve.resolve(in);
    }

    @PostMapping("/meals")
    public ResponseEntity<HealthModels.MealLogResponse> createMeal(
            Authentication auth,
            @RequestBody HealthModels.CreateMealRequest req
    ) {
        requireFeature();
        String userId = ownerId(auth);
        var meal = mealService.create(userId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(HealthModels.MealLogResponse.from(meal));
    }

    @GetMapping("/meals")
    public List<HealthModels.MealLogResponse> listMeals(
            Authentication auth,
            @RequestParam("date") String date
    ) {
        requireFeature();
        String userId = ownerId(auth);
        return mealService.list(userId, parseDate(date)).stream()
                .map(HealthModels.MealLogResponse::from)
                .toList();
    }

    @DeleteMapping("/meals/{id}")
    public ResponseEntity<Void> deleteMeal(Authentication auth, @PathVariable("id") String id) {
        requireFeature();
        mealService.delete(ownerId(auth), parseUuid(id, "invalid meal id"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/body-metrics")
    public ResponseEntity<HealthModels.BodyMetricResponse> createBody(
            Authentication auth,
            @RequestBody HealthModels.CreateBodyMetricRequest req
    ) {
        requireFeature();
        return ResponseEntity.status(HttpStatus.CREATED).body(profile.recordBody(ownerId(auth), req));
    }

    @GetMapping("/body-metrics/latest")
    public HealthModels.BodyMetricResponse latestBody(Authentication auth) {
        requireFeature();
        return profile.latestBody(ownerId(auth));
    }

    @GetMapping("/body-metrics")
    public List<HealthModels.BodyMetricResponse> bodyHistory(
            Authentication auth,
            @RequestParam("from") String from,
            @RequestParam("to") String to
    ) {
        requireFeature();
        return profile.bodyHistory(ownerId(auth), parseDate(from), parseDate(to));
    }

    @PutMapping("/goals")
    public HealthModels.GoalResponse upsertGoals(
            Authentication auth,
            @RequestBody HealthModels.UpsertGoalRequest req
    ) {
        requireFeature();
        return profile.upsertGoal(ownerId(auth), req);
    }

    @GetMapping("/goals")
    public HealthModels.GoalResponse getGoals(Authentication auth) {
        requireFeature();
        return profile.getGoal(ownerId(auth));
    }

    @GetMapping("/goals/meal-plan")
    public List<HealthModels.MealPlanSlotResponse> mealPlan(Authentication auth) {
        requireFeature();
        return profile.mealPlan(ownerId(auth));
    }

    @PostMapping("/burns")
    public ResponseEntity<HealthModels.BurnLogResponse> createBurn(
            Authentication auth,
            @RequestBody HealthModels.CreateBurnRequest req
    ) {
        requireFeature();
        return ResponseEntity.status(HttpStatus.CREATED).body(profile.createBurn(ownerId(auth), req));
    }

    @GetMapping("/burns")
    public List<HealthModels.BurnLogResponse> listBurns(
            Authentication auth,
            @RequestParam("date") String date
    ) {
        requireFeature();
        return profile.listBurns(ownerId(auth), parseDate(date));
    }

    @DeleteMapping("/burns/{id}")
    public ResponseEntity<Void> deleteBurn(Authentication auth, @PathVariable("id") String id) {
        requireFeature();
        profile.deleteBurn(ownerId(auth), parseUuid(id, "invalid burn id"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/check/today")
    public HealthModels.TodayCheckResponse today(
            Authentication auth,
            @RequestParam("date") String date
    ) {
        requireFeature();
        return profile.todayCheck(ownerId(auth), parseDate(date));
    }

    @GetMapping("/check/weekly")
    public HealthModels.WeeklyCheckResponse weekly(
            Authentication auth,
            @RequestParam(value = "days", defaultValue = "7") int days
    ) {
        requireFeature();
        return profile.weeklyCheck(ownerId(auth), days);
    }

    private void requireFeature() {
        if (!props.healthEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "health feature disabled");
        }
    }

    private static String ownerId(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid user");
        }
        return auth.getName();
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date is required (YYYY-MM-DD)");
        }
        try {
            return CivilDay.parse(raw);
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date must be YYYY-MM-DD");
        }
    }

    private static UUID parseUuid(String id, String err) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, err);
        }
    }

    private static void validateImage(byte[] decoded, String mime) {
        if (decoded.length == 0) {
            return;
        }
        if (decoded.length > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image too large");
        }
        String normalized = mime == null ? "" : mime.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_MIME.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported image_mime");
        }
    }
}
