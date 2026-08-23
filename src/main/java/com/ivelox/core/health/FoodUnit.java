package com.ivelox.core.health;

import java.util.Locale;
import java.util.Optional;

public enum FoodUnit {
    G("g"),
    ML("ml"),
    SERVING("serving"),
    PIECE("piece");

    private final String value;

    FoodUnit(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Optional<FoodUnit> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        for (FoodUnit u : values()) {
            if (u.value.equals(v)) {
                return Optional.of(u);
            }
        }
        return Optional.empty();
    }

    public static boolean isValid(String raw) {
        return parse(raw).isPresent();
    }
}
