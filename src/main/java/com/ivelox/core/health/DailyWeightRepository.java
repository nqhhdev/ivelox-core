package com.ivelox.core.health;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DailyWeightRepository {

    private final JdbcTemplate jdbc;

    public DailyWeightRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(String userId, LocalDate day, double weightKg) {
        Instant now = Instant.now();
        int n = jdbc.update("""
                update daily_weight_logs set weight_kg = ?, recorded_at = ?
                where user_id = ? and log_day = ?
                """, weightKg, Timestamp.from(now), userId, Date.valueOf(day));
        if (n == 0) {
            jdbc.update("""
                    insert into daily_weight_logs (user_id, log_day, weight_kg, recorded_at)
                    values (?, ?, ?, ?)
                    """, userId, Date.valueOf(day), weightKg, Timestamp.from(now));
        }
    }

    public Optional<Double> find(String userId, LocalDate day) {
        try {
            Double w = jdbc.queryForObject("""
                    select weight_kg from daily_weight_logs where user_id = ? and log_day = ?
                    """, Double.class, userId, Date.valueOf(day));
            return Optional.ofNullable(w);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
