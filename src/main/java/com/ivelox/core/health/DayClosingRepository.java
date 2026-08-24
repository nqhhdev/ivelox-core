package com.ivelox.core.health;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DayClosingRepository {

    public record DayClosing(
            String userId,
            LocalDate day,
            double eatenKcal,
            double burnedKcal,
            double netKcal,
            double proteinG,
            double carbG,
            double fatG,
            Integer kcalTarget,
            Integer proteinTarget,
            Integer carbTarget,
            Integer fatTarget,
            String tipsJson,
            Instant closedAt
    ) {
    }

    private final JdbcTemplate jdbc;

    public DayClosingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<DayClosing> MAPPER = (rs, i) -> new DayClosing(
            rs.getString("user_id"),
            rs.getDate("log_day").toLocalDate(),
            rs.getDouble("eaten_kcal"),
            rs.getDouble("burned_kcal"),
            rs.getDouble("net_kcal"),
            rs.getDouble("protein_g"),
            rs.getDouble("carb_g"),
            rs.getDouble("fat_g"),
            rs.getObject("kcal_target", Integer.class),
            rs.getObject("protein_g_target", Integer.class),
            rs.getObject("carb_g_target", Integer.class),
            rs.getObject("fat_g_target", Integer.class),
            rs.getString("tips_json"),
            rs.getTimestamp("closed_at").toInstant()
    );

    public void upsert(DayClosing c) {
        int n = jdbc.update("""
                update day_closings set
                  eaten_kcal=?, burned_kcal=?, net_kcal=?, protein_g=?, carb_g=?, fat_g=?,
                  kcal_target=?, protein_g_target=?, carb_g_target=?, fat_g_target=?,
                  tips_json=?, closed_at=?
                where user_id=? and log_day=?
                """,
                c.eatenKcal(), c.burnedKcal(), c.netKcal(), c.proteinG(), c.carbG(), c.fatG(),
                c.kcalTarget(), c.proteinTarget(), c.carbTarget(), c.fatTarget(),
                c.tipsJson(), Timestamp.from(c.closedAt()),
                c.userId(), Date.valueOf(c.day()));
        if (n == 0) {
            jdbc.update("""
                    insert into day_closings (
                      user_id, log_day, eaten_kcal, burned_kcal, net_kcal, protein_g, carb_g, fat_g,
                      kcal_target, protein_g_target, carb_g_target, fat_g_target, tips_json, closed_at
                    ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    c.userId(), Date.valueOf(c.day()), c.eatenKcal(), c.burnedKcal(), c.netKcal(),
                    c.proteinG(), c.carbG(), c.fatG(),
                    c.kcalTarget(), c.proteinTarget(), c.carbTarget(), c.fatTarget(),
                    c.tipsJson(), Timestamp.from(c.closedAt()));
        }
    }

    public Optional<DayClosing> find(String userId, LocalDate day) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select * from day_closings where user_id = ? and log_day = ?
                    """, MAPPER, userId, Date.valueOf(day)));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
