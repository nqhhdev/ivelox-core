package com.ivelox.core.health;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class BodyMetricsRepository {

    private final JdbcTemplate jdbc;

    public BodyMetricsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<HealthModels.BodyMetric> MAPPER = (rs, i) -> new HealthModels.BodyMetric(
            UUID.fromString(rs.getString("id")),
            rs.getString("user_id"),
            rs.getDouble("height_cm"),
            rs.getDouble("weight_kg"),
            rs.getDouble("bmi"),
            rs.getTimestamp("recorded_at").toInstant()
    );

    public HealthModels.BodyMetric create(HealthModels.BodyMetric m) {
        UUID id = m.id() != null ? m.id() : UUID.randomUUID();
        Instant recorded = m.recordedAt() != null ? m.recordedAt() : Instant.now();
        Instant created = Instant.now();
        jdbc.update("""
                insert into body_metrics (id, user_id, height_cm, weight_kg, bmi, recorded_at, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                id.toString(), m.userId(), m.heightCm(), m.weightKg(), m.bmi(),
                Timestamp.from(recorded), Timestamp.from(created));
        return new HealthModels.BodyMetric(id, m.userId(), m.heightCm(), m.weightKg(), m.bmi(), recorded);
    }

    public Optional<HealthModels.BodyMetric> latest(String userId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    """
                            select id, user_id, height_cm, weight_kg, bmi, recorded_at
                            from body_metrics where user_id = ?
                            order by recorded_at desc limit 1
                            """,
                    MAPPER, userId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<HealthModels.BodyMetric> history(String userId, LocalDate from, LocalDate to) {
        Instant start = CivilDay.boundsUtc(from)[0];
        Instant end = CivilDay.boundsUtc(to)[1];
        return jdbc.query(
                """
                        select id, user_id, height_cm, weight_kg, bmi, recorded_at
                        from body_metrics
                        where user_id = ? and recorded_at >= ? and recorded_at < ?
                        order by recorded_at desc
                        """,
                MAPPER, userId, Timestamp.from(start), Timestamp.from(end));
    }
}
