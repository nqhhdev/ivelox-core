package com.ivelox.core.finance;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class FinanceRepository {

    private final JdbcTemplate jdbc;

    public FinanceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static LocalDate date(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        Date d = rs.getDate(col);
        return d == null ? null : d.toLocalDate();
    }

    private static UUID uuid(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        String raw = rs.getString(col);
        return raw == null ? null : UUID.fromString(raw);
    }

    private static final RowMapper<FinanceModels.Tx> TX = (rs, n) -> new FinanceModels.Tx(
            UUID.fromString(rs.getString("id")),
            rs.getString("user_id"),
            rs.getString("kind"),
            rs.getLong("amount_minor"),
            rs.getString("currency").trim(),
            rs.getString("category"),
            rs.getString("note"),
            date(rs, "occurred_on"),
            uuid(rs, "income_id"),
            uuid(rs, "saving_goal_id"),
            uuid(rs, "loan_id"),
            uuid(rs, "fixed_expense_id"),
            rs.getTimestamp("created_at").toInstant()
    );

    private static final RowMapper<FinanceModels.Income> INCOME = (rs, n) -> new FinanceModels.Income(
            UUID.fromString(rs.getString("id")),
            rs.getString("user_id"),
            rs.getString("name"),
            rs.getLong("amount_minor"),
            rs.getString("currency").trim(),
            rs.getInt("day_of_month"),
            rs.getString("recurrence"),
            rs.getTimestamp("created_at").toInstant()
    );

    private static final RowMapper<FinanceModels.SavingGoal> SAVING = (rs, n) -> new FinanceModels.SavingGoal(
            UUID.fromString(rs.getString("id")),
            rs.getString("user_id"),
            rs.getString("name"),
            rs.getLong("target_amount_minor"),
            rs.getLong("monthly_amount_minor"),
            rs.getString("currency").trim(),
            rs.getInt("day_of_month"),
            rs.getTimestamp("created_at").toInstant()
    );

    private static final RowMapper<FinanceModels.Loan> LOAN = (rs, n) -> new FinanceModels.Loan(
            UUID.fromString(rs.getString("id")),
            rs.getString("user_id"),
            rs.getString("name"),
            rs.getLong("principal_minor"),
            rs.getLong("monthly_payment_minor"),
            rs.getString("currency").trim(),
            rs.getInt("day_of_month"),
            rs.getString("note"),
            rs.getTimestamp("created_at").toInstant()
    );

    private static final RowMapper<FinanceModels.FixedExpense> FIXED = (rs, n) -> new FinanceModels.FixedExpense(
            UUID.fromString(rs.getString("id")),
            rs.getString("user_id"),
            rs.getString("name"),
            rs.getLong("amount_minor"),
            rs.getString("currency").trim(),
            rs.getInt("day_of_month"),
            rs.getTimestamp("created_at").toInstant()
    );

    private static final RowMapper<FinanceModels.Settings> SETTINGS = (rs, n) -> new FinanceModels.Settings(
            rs.getString("user_id"),
            rs.getString("home_currency").trim(),
            rs.getInt("digest_hour"),
            rs.getBoolean("digest_enabled"),
            rs.getBoolean("over_slot_alert"),
            rs.getBoolean("over_month_alert")
    );

    public FinanceModels.Settings settings(String userId) {
        List<FinanceModels.Settings> rows = jdbc.query(
                "select * from finance_settings where user_id = ?", SETTINGS, userId);
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        jdbc.update("""
                insert into finance_settings (user_id)
                select ? where not exists (select 1 from finance_settings where user_id = ?)
                """, userId, userId);
        return jdbc.queryForObject("select * from finance_settings where user_id = ?", SETTINGS, userId);
    }

    public void saveSettings(FinanceModels.Settings s) {
        jdbc.update("""
                update finance_settings
                set home_currency = ?, digest_hour = ?, digest_enabled = ?,
                    over_slot_alert = ?, over_month_alert = ?
                where user_id = ?
                """,
                s.homeCurrency(), s.digestHour(), s.digestEnabled(),
                s.overSlotAlert(), s.overMonthAlert(), s.userId());
    }

    public FinanceModels.Tx insertTx(FinanceModels.Tx tx) {
        UUID id = tx.id() != null ? tx.id() : UUID.randomUUID();
        Instant created = tx.createdAt() != null ? tx.createdAt() : Instant.now();
        jdbc.update("""
                insert into finance_transactions (
                  id, user_id, kind, amount_minor, currency, category, note, occurred_on,
                  income_id, saving_goal_id, loan_id, fixed_expense_id, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, tx.userId(), tx.kind(), tx.amountMinor(), tx.currency(),
                tx.category(), tx.note() == null ? "" : tx.note(), Date.valueOf(tx.occurredOn()),
                tx.incomeId(), tx.savingGoalId(), tx.loanId(), tx.fixedExpenseId(),
                Timestamp.from(created));
        return new FinanceModels.Tx(
                id, tx.userId(), tx.kind(), tx.amountMinor(), tx.currency(), tx.category(),
                tx.note() == null ? "" : tx.note(), tx.occurredOn(),
                tx.incomeId(), tx.savingGoalId(), tx.loanId(), tx.fixedExpenseId(), created);
    }

    public List<FinanceModels.Tx> txsInMonth(String userId, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        return jdbc.query("""
                select * from finance_transactions
                where user_id = ? and occurred_on >= ? and occurred_on <= ?
                """, TX, userId, Date.valueOf(start), Date.valueOf(end));
    }

    public List<FinanceModels.Tx> listTx(
            String userId, LocalDate from, LocalDate to, String kind, String category, String currency,
            int limit, LocalDate cursorDay, UUID cursorId
    ) {
        StringBuilder sql = new StringBuilder("""
                select * from finance_transactions
                where user_id = ? and occurred_on >= ? and occurred_on <= ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.add(Date.valueOf(from));
        args.add(Date.valueOf(to));
        if (kind != null) {
            sql.append(" and kind = ?");
            args.add(kind);
        }
        if (category != null) {
            sql.append(" and category = ?");
            args.add(category);
        }
        if (currency != null) {
            sql.append(" and currency = ?");
            args.add(currency);
        }
        if (cursorDay != null && cursorId != null) {
            sql.append(" and (occurred_on < ? or (occurred_on = ? and id < ?))");
            args.add(Date.valueOf(cursorDay));
            args.add(Date.valueOf(cursorDay));
            args.add(cursorId);
        }
        sql.append(" order by occurred_on desc, id desc limit ?");
        args.add(limit);
        return jdbc.query(sql.toString(), TX, args.toArray());
    }

    public Optional<FinanceModels.Tx> findTx(String userId, UUID id) {
        List<FinanceModels.Tx> rows = jdbc.query(
                "select * from finance_transactions where id = ? and user_id = ?", TX, id, userId);
        return rows.stream().findFirst();
    }

    public int deleteTx(String userId, UUID id) {
        return jdbc.update("delete from finance_transactions where id = ? and user_id = ?", id, userId);
    }

    /** Sum of loan_payment transactions for one loan, in the loan's currency, across all history. */
    public long sumLoanPayments(String userId, UUID loanId, String currency) {
        Long sum = jdbc.queryForObject("""
                select coalesce(sum(amount_minor), 0) from finance_transactions
                where user_id = ? and loan_id = ? and kind = 'loan_payment' and currency = ?
                """, Long.class, userId, loanId, currency);
        return sum == null ? 0L : sum;
    }

    /** Sum of saving transactions for one goal, in the goal's currency, across all history. */
    public long sumSavingContributions(String userId, UUID goalId, String currency) {
        Long sum = jdbc.queryForObject("""
                select coalesce(sum(amount_minor), 0) from finance_transactions
                where user_id = ? and saving_goal_id = ? and kind = 'saving' and currency = ?
                """, Long.class, userId, goalId, currency);
        return sum == null ? 0L : sum;
    }

    /**
     * Atomically claims a monthly due posting slot. Returns true if this call claimed it
     * (caller should post the transaction), false if it was already claimed (already posted).
     */
    public boolean tryClaimDuePosting(String userId, String kind, UUID sourceId, YearMonth period) {
        try {
            jdbc.update("""
                    insert into finance_due_postings (id, user_id, kind, source_id, period, created_at)
                    values (?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(), userId, kind, sourceId, period.toString(), Timestamp.from(Instant.now()));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Optional<LocalDate> lastPostedOn(String userId) {
        List<LocalDate> rows = jdbc.query(
                "select last_posted_on from finance_due_cursor where user_id = ?",
                (rs, n) -> date(rs, "last_posted_on"), userId);
        return rows.isEmpty() || rows.get(0) == null ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void advanceLastPostedOn(String userId, LocalDate day) {
        int updated = jdbc.update(
                "update finance_due_cursor set last_posted_on = ? where user_id = ?",
                Date.valueOf(day), userId);
        if (updated == 0) {
            jdbc.update(
                    "insert into finance_due_cursor (user_id, last_posted_on) values (?, ?)",
                    userId, Date.valueOf(day));
        }
    }

    public List<FinanceModels.Income> incomes(String userId) {
        return jdbc.query("select * from finance_incomes where user_id = ? order by created_at", INCOME, userId);
    }

    public Optional<FinanceModels.Income> findIncome(String userId, UUID id) {
        return jdbc.query("select * from finance_incomes where id = ? and user_id = ?", INCOME, id, userId)
                .stream().findFirst();
    }

    public FinanceModels.Income insertIncome(FinanceModels.Income row) {
        UUID id = row.id() != null ? row.id() : UUID.randomUUID();
        Instant created = Instant.now();
        jdbc.update("""
                insert into finance_incomes
                (id, user_id, name, amount_minor, currency, day_of_month, recurrence, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, row.userId(), row.name(), row.amountMinor(), row.currency(),
                row.dayOfMonth(), row.recurrence(), Timestamp.from(created));
        return new FinanceModels.Income(id, row.userId(), row.name(), row.amountMinor(),
                row.currency(), row.dayOfMonth(), row.recurrence(), created);
    }

    public void updateIncome(FinanceModels.Income row) {
        jdbc.update("""
                update finance_incomes set name = ?, amount_minor = ?, day_of_month = ?, recurrence = ?
                where id = ? and user_id = ?
                """, row.name(), row.amountMinor(), row.dayOfMonth(), row.recurrence(), row.id(), row.userId());
    }

    public int deleteIncome(String userId, UUID id) {
        return jdbc.update("delete from finance_incomes where id = ? and user_id = ?", id, userId);
    }

    public List<FinanceModels.SavingGoal> savings(String userId) {
        return jdbc.query("select * from finance_saving_goals where user_id = ? order by created_at", SAVING, userId);
    }

    public Optional<FinanceModels.SavingGoal> findSaving(String userId, UUID id) {
        return jdbc.query("select * from finance_saving_goals where id = ? and user_id = ?", SAVING, id, userId)
                .stream().findFirst();
    }

    public FinanceModels.SavingGoal insertSaving(FinanceModels.SavingGoal row) {
        UUID id = row.id() != null ? row.id() : UUID.randomUUID();
        Instant created = Instant.now();
        jdbc.update("""
                insert into finance_saving_goals
                (id, user_id, name, target_amount_minor, monthly_amount_minor, currency, day_of_month, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, row.userId(), row.name(), row.targetAmountMinor(), row.monthlyAmountMinor(),
                row.currency(), row.dayOfMonth(), Timestamp.from(created));
        return new FinanceModels.SavingGoal(id, row.userId(), row.name(), row.targetAmountMinor(),
                row.monthlyAmountMinor(), row.currency(), row.dayOfMonth(), created);
    }

    public void updateSaving(FinanceModels.SavingGoal row) {
        jdbc.update("""
                update finance_saving_goals
                set name = ?, target_amount_minor = ?, monthly_amount_minor = ?, day_of_month = ?
                where id = ? and user_id = ?
                """, row.name(), row.targetAmountMinor(), row.monthlyAmountMinor(), row.dayOfMonth(),
                row.id(), row.userId());
    }

    public int deleteSaving(String userId, UUID id) {
        return jdbc.update("delete from finance_saving_goals where id = ? and user_id = ?", id, userId);
    }

    public List<FinanceModels.Loan> loans(String userId) {
        return jdbc.query("select * from finance_loans where user_id = ? order by created_at", LOAN, userId);
    }

    public Optional<FinanceModels.Loan> findLoan(String userId, UUID id) {
        return jdbc.query("select * from finance_loans where id = ? and user_id = ?", LOAN, id, userId)
                .stream().findFirst();
    }

    public FinanceModels.Loan insertLoan(FinanceModels.Loan row) {
        UUID id = row.id() != null ? row.id() : UUID.randomUUID();
        Instant created = Instant.now();
        jdbc.update("""
                insert into finance_loans
                (id, user_id, name, principal_minor, monthly_payment_minor, currency, day_of_month, note, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, row.userId(), row.name(), row.principalMinor(), row.monthlyPaymentMinor(),
                row.currency(), row.dayOfMonth(), row.note() == null ? "" : row.note(), Timestamp.from(created));
        return new FinanceModels.Loan(id, row.userId(), row.name(), row.principalMinor(),
                row.monthlyPaymentMinor(), row.currency(), row.dayOfMonth(),
                row.note() == null ? "" : row.note(), created);
    }

    public void updateLoan(FinanceModels.Loan row) {
        jdbc.update("""
                update finance_loans
                set name = ?, principal_minor = ?, monthly_payment_minor = ?, day_of_month = ?, note = ?
                where id = ? and user_id = ?
                """, row.name(), row.principalMinor(), row.monthlyPaymentMinor(),
                row.dayOfMonth(), row.note(), row.id(), row.userId());
    }

    public int deleteLoan(String userId, UUID id) {
        return jdbc.update("delete from finance_loans where id = ? and user_id = ?", id, userId);
    }

    public List<FinanceModels.FixedExpense> fixed(String userId) {
        return jdbc.query("select * from finance_fixed_expenses where user_id = ? order by created_at", FIXED, userId);
    }

    public Optional<FinanceModels.FixedExpense> findFixed(String userId, UUID id) {
        return jdbc.query("select * from finance_fixed_expenses where id = ? and user_id = ?", FIXED, id, userId)
                .stream().findFirst();
    }

    public FinanceModels.FixedExpense insertFixed(FinanceModels.FixedExpense row) {
        UUID id = row.id() != null ? row.id() : UUID.randomUUID();
        Instant created = Instant.now();
        jdbc.update("""
                insert into finance_fixed_expenses
                (id, user_id, name, amount_minor, currency, day_of_month, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                id, row.userId(), row.name(), row.amountMinor(), row.currency(),
                row.dayOfMonth(), Timestamp.from(created));
        return new FinanceModels.FixedExpense(id, row.userId(), row.name(), row.amountMinor(),
                row.currency(), row.dayOfMonth(), created);
    }

    public void updateFixed(FinanceModels.FixedExpense row) {
        jdbc.update("""
                update finance_fixed_expenses set name = ?, amount_minor = ?, day_of_month = ?
                where id = ? and user_id = ?
                """, row.name(), row.amountMinor(), row.dayOfMonth(), row.id(), row.userId());
    }

    public int deleteFixed(String userId, UUID id) {
        return jdbc.update("delete from finance_fixed_expenses where id = ? and user_id = ?", id, userId);
    }

    public List<String> currenciesInUse(String userId) {
        return jdbc.query("""
                select distinct currency from (
                  select currency from finance_transactions where user_id = ?
                  union select currency from finance_incomes where user_id = ?
                  union select currency from finance_saving_goals where user_id = ?
                  union select currency from finance_loans where user_id = ?
                  union select currency from finance_fixed_expenses where user_id = ?
                  union select home_currency from finance_settings where user_id = ?
                ) x order by 1
                """, (rs, n) -> rs.getString(1).trim(),
                userId, userId, userId, userId, userId, userId);
    }

    public boolean insertNotifyIfAbsent(String userId, String type, LocalDate civilDay, String currency) {
        try {
            jdbc.update("""
                    insert into finance_notify_log
                    (id, user_id, channel, type, civil_day, currency, sent_at)
                    values (?, ?, 'telegram', ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(), userId, type, Date.valueOf(civilDay), currency, Timestamp.from(Instant.now()));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Removes a reserved dedup record after a failed send, so a later retry is not permanently blocked. */
    public void deleteNotifyLog(String userId, String type, LocalDate civilDay, String currency) {
        jdbc.update("""
                delete from finance_notify_log
                where user_id = ? and type = ? and civil_day = ? and currency = ?
                """, userId, type, Date.valueOf(civilDay), currency);
    }
}
