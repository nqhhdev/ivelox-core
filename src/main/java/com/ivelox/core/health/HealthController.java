package com.ivelox.core.health;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    public HealthController(
            IveloxProperties props,
            FoodResolveService foodResolve,
            MealService mealService
    ) {
        this.props = props;
        this.foodResolve = foodResolve;
        this.mealService = mealService;
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
        LocalDate day = parseDate(date);
        return mealService.list(userId, day).stream()
                .map(HealthModels.MealLogResponse::from)
                .toList();
    }

    @DeleteMapping("/meals/{id}")
    public ResponseEntity<Void> deleteMeal(Authentication auth, @PathVariable("id") String id) {
        requireFeature();
        String userId = ownerId(auth);
        UUID mealId;
        try {
            mealId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid meal id");
        }
        mealService.delete(userId, mealId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/check/today")
    public HealthModels.DayMealSummary today(
            Authentication auth,
            @RequestParam("date") String date
    ) {
        requireFeature();
        String userId = ownerId(auth);
        return mealService.todaySummary(userId, parseDate(date));
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

    /** Unused helper kept for clarity — error bodies use Spring default message. */
    @SuppressWarnings("unused")
    private static Map<String, String> error(String msg) {
        return Map.of("error", msg);
    }
}
