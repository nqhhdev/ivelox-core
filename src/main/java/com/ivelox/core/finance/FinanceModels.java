package com.ivelox.core.finance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class FinanceModels {

    private FinanceModels() {
    }

    public record Income(
            UUID id,
            String userId,
            String name,
            long amountMinor,
            String currency,
            int dayOfMonth,
            String recurrence,
            Instant createdAt
    ) {
    }

    public record SavingGoal(
            UUID id,
            String userId,
            String name,
            long targetAmountMinor,
            long monthlyAmountMinor,
            String currency,
            int dayOfMonth,
            Instant createdAt
    ) {
    }

    public record Loan(
            UUID id,
            String userId,
            String name,
            long principalMinor,
            long monthlyPaymentMinor,
            String currency,
            int dayOfMonth,
            String note,
            Instant createdAt
    ) {
    }

    public record FixedExpense(
            UUID id,
            String userId,
            String name,
            long amountMinor,
            String currency,
            int dayOfMonth,
            Instant createdAt
    ) {
    }

    public record Tx(
            UUID id,
            String userId,
            String kind,
            long amountMinor,
            String currency,
            String category,
            String note,
            LocalDate occurredOn,
            UUID incomeId,
            UUID savingGoalId,
            UUID loanId,
            UUID fixedExpenseId,
            Instant createdAt
    ) {
    }

    public record Settings(
            String userId,
            String homeCurrency,
            int digestHour,
            boolean digestEnabled,
            boolean overSlotAlert,
            boolean overMonthAlert
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MoneyIn(
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TxCreate(
            String kind,
            BigDecimal amount,
            String currency,
            String category,
            String note,
            @JsonProperty("occurred_on") LocalDate occurredOn,
            @JsonProperty("income_id") UUID incomeId,
            @JsonProperty("saving_goal_id") UUID savingGoalId,
            @JsonProperty("loan_id") UUID loanId,
            @JsonProperty("fixed_expense_id") UUID fixedExpenseId
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TxView(
            String id,
            String kind,
            BigDecimal amount,
            @JsonProperty("amount_minor") long amountMinor,
            String currency,
            String category,
            String note,
            @JsonProperty("occurred_on") LocalDate occurredOn,
            @JsonProperty("income_id") UUID incomeId,
            @JsonProperty("saving_goal_id") UUID savingGoalId,
            @JsonProperty("loan_id") UUID loanId,
            @JsonProperty("fixed_expense_id") UUID fixedExpenseId,
            @JsonProperty("created_at") Instant createdAt
    ) {
        public static TxView of(Tx tx) {
            var money = FinanceMoney.MoneyDto.of(tx.amountMinor(), tx.currency());
            return new TxView(
                    tx.id().toString(),
                    tx.kind(),
                    money.amount(),
                    money.amountMinor(),
                    money.currency(),
                    tx.category(),
                    tx.note(),
                    tx.occurredOn(),
                    tx.incomeId(),
                    tx.savingGoalId(),
                    tx.loanId(),
                    tx.fixedExpenseId(),
                    tx.createdAt()
            );
        }
    }

    public record TxPage(
            List<TxView> items,
            @JsonProperty("next_cursor") String nextCursor
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IncomeWrite(
            String name,
            BigDecimal amount,
            String currency,
            @JsonProperty("day_of_month") Integer dayOfMonth,
            String recurrence
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SavingWrite(
            String name,
            @JsonProperty("target_amount") BigDecimal targetAmount,
            @JsonProperty("monthly_amount") BigDecimal monthlyAmount,
            String currency,
            @JsonProperty("day_of_month") Integer dayOfMonth
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LoanWrite(
            String name,
            BigDecimal principal,
            @JsonProperty("monthly_payment") BigDecimal monthlyPayment,
            String currency,
            @JsonProperty("day_of_month") Integer dayOfMonth,
            String note,
            @JsonProperty("disburse_now") Boolean disburseNow
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FixedWrite(
            String name,
            BigDecimal amount,
            String currency,
            @JsonProperty("day_of_month") Integer dayOfMonth
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SettingsWrite(
            @JsonProperty("home_currency") String homeCurrency,
            @JsonProperty("digest_hour") Integer digestHour,
            @JsonProperty("digest_enabled") Boolean digestEnabled,
            @JsonProperty("over_slot_alert") Boolean overSlotAlert,
            @JsonProperty("over_month_alert") Boolean overMonthAlert
    ) {
    }

    public record SettingsView(
            @JsonProperty("home_currency") String homeCurrency,
            @JsonProperty("digest_hour") int digestHour,
            @JsonProperty("digest_enabled") boolean digestEnabled,
            @JsonProperty("over_slot_alert") boolean overSlotAlert,
            @JsonProperty("over_month_alert") boolean overMonthAlert
    ) {
    }

    public record IncomeView(
            String id,
            String name,
            BigDecimal amount,
            @JsonProperty("amount_minor") long amountMinor,
            String currency,
            @JsonProperty("day_of_month") int dayOfMonth,
            String recurrence,
            @JsonProperty("created_at") Instant createdAt
    ) {
        public static IncomeView of(Income row) {
            var m = FinanceMoney.MoneyDto.of(row.amountMinor(), row.currency());
            return new IncomeView(row.id().toString(), row.name(), m.amount(), m.amountMinor(),
                    m.currency(), row.dayOfMonth(), row.recurrence(), row.createdAt());
        }
    }

    public record SavingView(
            String id,
            String name,
            String currency,
            FinanceMoney.MoneyDto target,
            FinanceMoney.MoneyDto contributed,
            FinanceMoney.MoneyDto monthly,
            @JsonProperty("day_of_month") int dayOfMonth
    ) {
    }

    public record LoanView(
            String id,
            String name,
            String currency,
            FinanceMoney.MoneyDto principal,
            FinanceMoney.MoneyDto remaining,
            @JsonProperty("monthly_payment") FinanceMoney.MoneyDto monthlyPayment,
            @JsonProperty("day_of_month") int dayOfMonth,
            String note
    ) {
    }

    public record FixedView(
            String id,
            String name,
            BigDecimal amount,
            @JsonProperty("amount_minor") long amountMinor,
            String currency,
            @JsonProperty("day_of_month") int dayOfMonth,
            @JsonProperty("created_at") Instant createdAt
    ) {
        public static FixedView of(FixedExpense row) {
            var m = FinanceMoney.MoneyDto.of(row.amountMinor(), row.currency());
            return new FixedView(row.id().toString(), row.name(), m.amount(), m.amountMinor(),
                    m.currency(), row.dayOfMonth(), row.createdAt());
        }
    }

    public record Upcoming(
            String kind,
            String name,
            @JsonProperty("due_on") LocalDate dueOn,
            FinanceMoney.MoneyDto amount
    ) {
    }

    public record Dashboard(
            String month,
            String today,
            @JsonProperty("is_current_month") boolean currentMonth,
            String currency,
            @JsonProperty("home_currency") String homeCurrency,
            @JsonProperty("currencies_in_use") List<String> currenciesInUse,
            FinanceMoney.MoneyDto pool,
            FinanceMoney.MoneyDto income,
            FinanceMoney.MoneyDto fixed,
            FinanceMoney.MoneyDto saving,
            @JsonProperty("loan_payment") FinanceMoney.MoneyDto loanPayment,
            @JsonProperty("loan_disbursement") FinanceMoney.MoneyDto loanDisbursement,
            @JsonProperty("daily_month") FinanceMoney.MoneyDto dailyMonth,
            @JsonProperty("daily_today") FinanceMoney.MoneyDto dailyToday,
            @JsonProperty("today_slot") FinanceMoney.MoneyDto todaySlot,
            @JsonProperty("today_left") FinanceMoney.MoneyDto todayLeft,
            @JsonProperty("remaining_month") FinanceMoney.MoneyDto remainingMonth,
            @JsonProperty("last_month_remaining") FinanceMoney.MoneyDto lastMonthRemaining,
            List<SavingView> savings,
            List<LoanView> loans,
            List<Upcoming> upcoming
    ) {
    }

    public record ItemList<T>(List<T> items) {
    }
}
