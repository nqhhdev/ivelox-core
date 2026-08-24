package com.ivelox.core.health;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MealImageRepository {

    public record MealImage(UUID mealLogId, String userId, String mime, byte[] bytes) {
    }

    private final JdbcTemplate jdbc;

    public MealImageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(MealImage img) {
        Instant now = Instant.now();
        int n = jdbc.update("""
                update meal_images set mime = ?, bytes = ?, created_at = ?
                where meal_log_id = ? and user_id = ?
                """, img.mime(), img.bytes(), Timestamp.from(now), img.mealLogId(), img.userId());
        if (n == 0) {
            jdbc.update("""
                    insert into meal_images (meal_log_id, user_id, mime, bytes, created_at)
                    values (?, ?, ?, ?, ?)
                    """, img.mealLogId(), img.userId(), img.mime(), img.bytes(), Timestamp.from(now));
        }
    }

    public Optional<MealImage> find(String userId, UUID mealLogId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select meal_log_id, user_id, mime, bytes from meal_images
                    where meal_log_id = ? and user_id = ?
                    """,
                    (rs, i) -> new MealImage(
                            UUID.fromString(rs.getString("meal_log_id")),
                            rs.getString("user_id"),
                            rs.getString("mime"),
                            rs.getBytes("bytes")
                    ),
                    mealLogId, userId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean exists(String userId, UUID mealLogId) {
        Integer n = jdbc.queryForObject("""
                select count(*) from meal_images where meal_log_id = ? and user_id = ?
                """, Integer.class, mealLogId, userId);
        return n != null && n > 0;
    }
}
