package com.ivelox.core.health;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MealLogRepository {

    private static final String COLUMNS = """
            id, user_id, food_cache_id, raw_input, image_url, quantity, unit,
            kcal, protein_g, carb_g, fat_g, meal_type, logged_at
            """;

    private final JdbcTemplate jdbc;

    public MealLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<HealthModels.MealLog> MAPPER = (rs, rowNum) -> {
        String foodCacheId = rs.getString("food_cache_id");
        return new HealthModels.MealLog(
                UUID.fromString(rs.getString("id")),
                rs.getString("user_id"),
                foodCacheId == null ? null : UUID.fromString(foodCacheId),
                rs.getString("raw_input"),
                rs.getString("image_url"),
                rs.getDouble("quantity"),
                rs.getString("unit"),
                rs.getDouble("kcal"),
                rs.getDouble("protein_g"),
                rs.getDouble("carb_g"),
                rs.getDouble("fat_g"),
                rs.getString("meal_type"),
                rs.getTimestamp("logged_at").toInstant()
        );
    };

    public HealthModels.MealLog create(HealthModels.MealLog meal) {
        UUID id = meal.id() != null ? meal.id() : UUID.randomUUID();
        Instant loggedAt = meal.loggedAt() != null ? meal.loggedAt() : Instant.now();
        Instant createdAt = Instant.now();
        jdbc.update("""
                insert into meal_logs (
                  id, user_id, food_cache_id, raw_input, image_url, quantity, unit,
                  kcal, protein_g, carb_g, fat_g, meal_type, logged_at, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                meal.userId(),
                meal.foodCacheId(),
                meal.rawInput() == null ? "" : meal.rawInput(),
                meal.imageUrl(),
                meal.quantity(),
                meal.unit(),
                meal.kcal(),
                meal.proteinG(),
                meal.carbG(),
                meal.fatG(),
                meal.mealType(),
                Timestamp.from(loggedAt),
                Timestamp.from(createdAt)
        );
        return new HealthModels.MealLog(
                id,
                meal.userId(),
                meal.foodCacheId(),
                meal.rawInput() == null ? "" : meal.rawInput(),
                meal.imageUrl(),
                meal.quantity(),
                meal.unit(),
                meal.kcal(),
                meal.proteinG(),
                meal.carbG(),
                meal.fatG(),
                meal.mealType(),
                loggedAt
        );
    }

    public List<HealthModels.MealLog> listByUserDate(String userId, LocalDate day) {
        Instant[] bounds = CivilDay.boundsUtc(day);
        return jdbc.query(
                "select " + COLUMNS + """
                         from meal_logs
                         where user_id = ? and logged_at >= ? and logged_at < ?
                         order by logged_at desc
                        """,
                MAPPER,
                userId,
                Timestamp.from(bounds[0]),
                Timestamp.from(bounds[1])
        );
    }

    public boolean delete(String userId, UUID id) {
        int n = jdbc.update(
                "delete from meal_logs where id = ? and user_id = ?",
                id,
                userId
        );
        return n > 0;
    }

    public HealthModels.DayMealSummary summarizeDay(String userId, LocalDate day) {
        Instant[] bounds = CivilDay.boundsUtc(day);
        return jdbc.queryForObject(
                """
                        select coalesce(sum(kcal),0), coalesce(sum(protein_g),0), coalesce(sum(carb_g),0),
                               coalesce(sum(fat_g),0), count(*)
                        from meal_logs
                        where user_id = ? and logged_at >= ? and logged_at < ?
                        """,
                (rs, rowNum) -> new HealthModels.DayMealSummary(
                        rs.getDouble(1),
                        rs.getDouble(2),
                        rs.getDouble(3),
                        rs.getDouble(4),
                        rs.getInt(5)
                ),
                userId,
                Timestamp.from(bounds[0]),
                Timestamp.from(bounds[1])
        );
    }

    public double sumKcalRange(String userId, LocalDate from, LocalDate toInclusive) {
        Instant start = CivilDay.boundsUtc(from)[0];
        Instant end = CivilDay.boundsUtc(toInclusive)[1];
        Double v = jdbc.queryForObject(
                """
                        select coalesce(sum(kcal),0) from meal_logs
                        where user_id = ? and logged_at >= ? and logged_at < ?
                        """,
                Double.class,
                userId,
                Timestamp.from(start),
                Timestamp.from(end)
        );
        return v == null ? 0 : v;
    }

    /** Sum kcal grouped by meal_type for a civil day (null/blank → "other"). */
    public java.util.Map<String, Double> sumKcalByMealType(String userId, LocalDate day) {
        Instant[] bounds = CivilDay.boundsUtc(day);
        java.util.Map<String, Double> out = new java.util.HashMap<>();
        jdbc.query("""
                select coalesce(nullif(trim(meal_type), ''), 'other') as mt, coalesce(sum(kcal),0) as k
                from meal_logs
                where user_id = ? and logged_at >= ? and logged_at < ?
                group by 1
                """,
                (rs, rowNum) -> {
                    out.put(rs.getString("mt"), rs.getDouble("k"));
                    return null;
                },
                userId,
                Timestamp.from(bounds[0]),
                Timestamp.from(bounds[1])
        );
        return out;
    }
}
