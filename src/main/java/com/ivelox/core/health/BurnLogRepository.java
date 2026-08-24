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
public class BurnLogRepository {

    private final JdbcTemplate jdbc;

    public BurnLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<HealthModels.BurnLog> MAPPER = (rs, i) -> new HealthModels.BurnLog(
            UUID.fromString(rs.getString("id")),
            rs.getString("user_id"),
            rs.getString("activity_name"),
            rs.getInt("duration_min"),
            rs.getDouble("kcal_burned"),
            rs.getString("source"),
            rs.getTimestamp("logged_at").toInstant()
    );

    public HealthModels.BurnLog create(HealthModels.BurnLog b) {
        UUID id = b.id() != null ? b.id() : UUID.randomUUID();
        Instant logged = b.loggedAt() != null ? b.loggedAt() : Instant.now();
        Instant created = Instant.now();
        jdbc.update("""
                insert into burn_logs (id, user_id, activity_name, duration_min, kcal_burned, source, logged_at, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, b.userId(), b.activityName(), b.durationMin(), b.kcalBurned(),
                b.source(), Timestamp.from(logged), Timestamp.from(created));
        return new HealthModels.BurnLog(id, b.userId(), b.activityName(), b.durationMin(),
                b.kcalBurned(), b.source(), logged);
    }

    public List<HealthModels.BurnLog> listByUserDate(String userId, LocalDate day) {
        Instant[] bounds = CivilDay.boundsUtc(day);
        return jdbc.query(
                """
                        select id, user_id, activity_name, duration_min, kcal_burned, source, logged_at
                        from burn_logs
                        where user_id = ? and logged_at >= ? and logged_at < ?
                        order by logged_at desc
                        """,
                MAPPER, userId, Timestamp.from(bounds[0]), Timestamp.from(bounds[1]));
    }

    public boolean delete(String userId, UUID id) {
        return jdbc.update("delete from burn_logs where id = ? and user_id = ?", id, userId) > 0;
    }

    public double sumKcal(String userId, LocalDate day) {
        Instant[] bounds = CivilDay.boundsUtc(day);
        Double v = jdbc.queryForObject(
                """
                        select coalesce(sum(kcal_burned),0) from burn_logs
                        where user_id = ? and logged_at >= ? and logged_at < ?
                        """,
                Double.class, userId, Timestamp.from(bounds[0]), Timestamp.from(bounds[1]));
        return v == null ? 0 : v;
    }

    public double sumKcalRange(String userId, LocalDate from, LocalDate toInclusive) {
        Instant start = CivilDay.boundsUtc(from)[0];
        Instant end = CivilDay.boundsUtc(toInclusive)[1];
        Double v = jdbc.queryForObject(
                """
                        select coalesce(sum(kcal_burned),0) from burn_logs
                        where user_id = ? and logged_at >= ? and logged_at < ?
                        """,
                Double.class, userId, Timestamp.from(start), Timestamp.from(end));
        return v == null ? 0 : v;
    }
}
