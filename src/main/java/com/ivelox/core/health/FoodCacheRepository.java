package com.ivelox.core.health;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class FoodCacheRepository {

    private static final String COLUMNS = """
            id, normalized_name, aliases, default_serving_qty, default_serving_unit,
            kcal, protein_g, carb_g, fat_g, source, confidence, updated_at
            """;

    private final JdbcTemplate jdbc;

    public FoodCacheRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<HealthModels.FoodCache> MAPPER = (rs, rowNum) -> new HealthModels.FoodCache(
            UUID.fromString(rs.getString("id")),
            rs.getString("normalized_name"),
            rs.getString("aliases"),
            rs.getDouble("default_serving_qty"),
            rs.getString("default_serving_unit"),
            rs.getDouble("kcal"),
            rs.getDouble("protein_g"),
            rs.getDouble("carb_g"),
            rs.getDouble("fat_g"),
            rs.getString("source"),
            rs.getDouble("confidence"),
            rs.getTimestamp("updated_at").toInstant()
    );

    public Optional<HealthModels.FoodCache> findByNormalizedName(String name) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "select " + COLUMNS + " from food_cache where normalized_name = ?",
                    MAPPER,
                    name
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public HealthModels.FoodCache upsertFromItem(HealthModels.FoodItem item, String source) {
        String normalized = FoodNameNormalizer.normalize(item.name());
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        // H2 MODE=PostgreSQL supports MERGE / ON CONFLICT differently; use portable upsert via update-then-insert.
        int updated = jdbc.update("""
                update food_cache set
                  default_serving_qty = ?,
                  default_serving_unit = ?,
                  kcal = ?,
                  protein_g = ?,
                  carb_g = ?,
                  fat_g = ?,
                  source = ?,
                  confidence = ?,
                  updated_at = ?
                where normalized_name = ?
                """,
                item.quantity(),
                item.unit(),
                item.kcal(),
                item.proteinG(),
                item.carbG(),
                item.fatG(),
                source,
                item.confidence(),
                Timestamp.from(now),
                normalized
        );
        if (updated == 0) {
            jdbc.update("""
                    insert into food_cache (
                      id, normalized_name, aliases, default_serving_qty, default_serving_unit,
                      kcal, protein_g, carb_g, fat_g, source, confidence, updated_at
                    ) values (?, ?, '', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    id.toString(),
                    normalized,
                    item.quantity(),
                    item.unit(),
                    item.kcal(),
                    item.proteinG(),
                    item.carbG(),
                    item.fatG(),
                    source,
                    item.confidence(),
                    Timestamp.from(now)
            );
        }
        return findByNormalizedName(normalized)
                .orElseThrow(() -> new IllegalStateException("upsert food cache failed"));
    }
}
