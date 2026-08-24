package com.ivelox.core.health;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MealSlotStateRepository {

    private final JdbcTemplate jdbc;

    public MealSlotStateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(String userId, LocalDate day, String mealType, String status, Integer baseKcal, Integer adjustedKcal) {
        Instant now = Instant.now();
        int n = jdbc.update("""
                update meal_slot_states set status = ?, base_kcal = ?, adjusted_kcal = ?, updated_at = ?
                where user_id = ? and log_day = ? and meal_type = ?
                """,
                status, baseKcal, adjustedKcal, Timestamp.from(now),
                userId, Date.valueOf(day), mealType);
        if (n == 0) {
            jdbc.update("""
                    insert into meal_slot_states (user_id, log_day, meal_type, status, base_kcal, adjusted_kcal, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?)
                    """,
                    userId, Date.valueOf(day), mealType, status, baseKcal, adjustedKcal, Timestamp.from(now));
        }
    }

    public Map<String, String> statusesForDay(String userId, LocalDate day) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select meal_type, status from meal_slot_states
                where user_id = ? and log_day = ?
                """, userId, Date.valueOf(day));
        Map<String, String> out = new HashMap<>();
        for (Map<String, Object> row : rows) {
            out.put(String.valueOf(row.get("meal_type")), String.valueOf(row.get("status")));
        }
        return out;
    }

    public Optional<String> status(String userId, LocalDate day, String mealType) {
        try {
            String s = jdbc.queryForObject("""
                    select status from meal_slot_states
                    where user_id = ? and log_day = ? and meal_type = ?
                    """, String.class, userId, Date.valueOf(day), mealType);
            return Optional.ofNullable(s);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
