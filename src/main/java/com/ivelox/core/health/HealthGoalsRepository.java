package com.ivelox.core.health;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class HealthGoalsRepository {

    private final JdbcTemplate jdbc;

    public HealthGoalsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Double numeric(ResultSet rs, String column) throws SQLException {
        BigDecimal v = rs.getObject(column, BigDecimal.class);
        return v == null ? null : v.doubleValue();
    }

    private static final RowMapper<HealthModels.HealthGoal> MAPPER = (rs, i) -> new HealthModels.HealthGoal(
            rs.getString("user_id"),
            numeric(rs, "height_cm"),
            numeric(rs, "weight_kg"),
            rs.getString("sex"),
            rs.getObject("age_years", Integer.class),
            rs.getString("activity_level"),
            numeric(rs, "weight_change_pct"),
            rs.getObject("weeks", Integer.class),
            numeric(rs, "target_weight_kg"),
            rs.getObject("daily_kcal_target", Integer.class),
            rs.getObject("daily_burn_target", Integer.class),
            rs.getDate("start_at") == null ? null : rs.getDate("start_at").toLocalDate(),
            rs.getDate("target_at") == null ? null : rs.getDate("target_at").toLocalDate(),
            rs.getString("meal_plan_json"),
            rs.getTimestamp("updated_at").toInstant()
    );

    public void upsert(HealthModels.HealthGoal g) {
        Instant now = Instant.now();
        int updated = jdbc.update("""
                update health_goals set
                  height_cm = ?, weight_kg = ?, sex = ?, age_years = ?, activity_level = ?,
                  weight_change_pct = ?, weeks = ?, target_weight_kg = ?, daily_kcal_target = ?,
                  daily_burn_target = ?, start_at = ?, target_at = ?, meal_plan_json = ?, updated_at = ?
                where user_id = ?
                """,
                g.heightCm(), g.weightKg(), g.sex(), g.ageYears(), g.activityLevel(),
                g.weightChangePct(), g.weeks(), g.targetWeightKg(), g.dailyKcalTarget(), g.dailyBurnTarget(),
                g.startAt() == null ? null : Date.valueOf(g.startAt()),
                g.targetAt() == null ? null : Date.valueOf(g.targetAt()),
                g.mealPlanJson(),
                Timestamp.from(now),
                g.userId()
        );
        if (updated == 0) {
            jdbc.update("""
                    insert into health_goals (
                      user_id, height_cm, weight_kg, sex, age_years, activity_level,
                      weight_change_pct, weeks, target_weight_kg, daily_kcal_target, daily_burn_target,
                      start_at, target_at, meal_plan_json, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    g.userId(), g.heightCm(), g.weightKg(), g.sex(), g.ageYears(), g.activityLevel(),
                    g.weightChangePct(), g.weeks(), g.targetWeightKg(), g.dailyKcalTarget(), g.dailyBurnTarget(),
                    g.startAt() == null ? null : Date.valueOf(g.startAt()),
                    g.targetAt() == null ? null : Date.valueOf(g.targetAt()),
                    g.mealPlanJson(),
                    Timestamp.from(now)
            );
        }
    }

    public Optional<HealthModels.HealthGoal> find(String userId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    """
                            select user_id, height_cm, weight_kg, sex, age_years, activity_level,
                                   weight_change_pct, weeks, target_weight_kg, daily_kcal_target, daily_burn_target,
                                   start_at, target_at, meal_plan_json, updated_at
                            from health_goals where user_id = ?
                            """,
                    MAPPER, userId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
