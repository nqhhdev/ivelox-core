package com.ivelox.core.health;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

public final class CivilDay {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private CivilDay() {
    }

    public static LocalDate parse(String raw) {
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException | NullPointerException e) {
            throw new DateTimeException("date must be YYYY-MM-DD");
        }
    }

    /** Inclusive start (UTC) and exclusive end (UTC) for the civil day. */
    public static Instant[] boundsUtc(LocalDate day) {
        ZonedDateTime startLocal = day.atStartOfDay(ZONE);
        Instant start = startLocal.toInstant();
        Instant end = startLocal.plusDays(1).toInstant();
        return new Instant[]{start, end};
    }
}
