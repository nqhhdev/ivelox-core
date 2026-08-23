package com.ivelox.core.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class CivilDayTest {

    @Test
    void boundsCoverFullIctDayInUtc() {
        Instant[] bounds = CivilDay.boundsUtc(LocalDate.of(2026, 8, 23));
        // ICT midnight = previous day 17:00 UTC
        assertEquals(Instant.parse("2026-08-22T17:00:00Z"), bounds[0]);
        assertEquals(Instant.parse("2026-08-23T17:00:00Z"), bounds[1]);
    }

    @Test
    void parseAcceptsIsoDate() {
        assertEquals(LocalDate.of(2026, 8, 23), CivilDay.parse("2026-08-23"));
    }

    @Test
    void earlyUtcMorningStillPreviousIctDayBound() {
        Instant[] day23 = CivilDay.boundsUtc(LocalDate.of(2026, 8, 23));
        Instant ict0100AsUtc = LocalDate.of(2026, 8, 23)
                .atTime(1, 0)
                .atZone(CivilDay.ZONE)
                .toInstant();
        assertTrue(!ict0100AsUtc.isBefore(day23[0]) && ict0100AsUtc.isBefore(day23[1]));
        assertEquals(ZoneOffset.UTC, ZoneOffset.UTC);
    }
}
