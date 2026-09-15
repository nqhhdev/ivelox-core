package com.ivelox.core.finance;

import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.stereotype.Component;

@Component
public class FinanceDuePoster {

    static final String OWNER = "owner";

    /** Bound how far back a first-ever catch-up can reach, so a fresh cursor doesn't replay all history. */
    private static final int MAX_CATCH_UP_DAYS = 60;

    private final FinanceRepository repo;

    public FinanceDuePoster(FinanceRepository repo) {
        this.repo = repo;
    }

    /** Posts dues for every civil day since the last successfully processed day, through today (inclusive). */
    public void catchUpTo(LocalDate today) {
        LocalDate start = repo.lastPostedOn(OWNER)
                .map(d -> d.plusDays(1))
                .orElse(today.minusDays(MAX_CATCH_UP_DAYS - 1L));
        if (start.isBefore(today.minusDays(MAX_CATCH_UP_DAYS - 1L))) {
            start = today.minusDays(MAX_CATCH_UP_DAYS - 1L);
        }
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            postForDay(day);
            repo.advanceLastPostedOn(OWNER, day);
        }
    }

    public void postForDay(LocalDate day) {
        if (day.getDayOfMonth() > 28) {
            return;
        }
        YearMonth month = YearMonth.from(day);
        int dom = day.getDayOfMonth();

        for (var income : repo.incomes(OWNER)) {
            if (!"monthly".equals(income.recurrence()) || income.dayOfMonth() != dom) {
                continue;
            }
            if (!repo.tryClaimDuePosting(OWNER, "income", income.id(), month)) {
                continue;
            }
            repo.insertTx(new FinanceModels.Tx(
                    null, OWNER, "income", income.amountMinor(), income.currency(), null,
                    income.name(), day, income.id(), null, null, null, null));
        }
        for (var fixed : repo.fixed(OWNER)) {
            if (fixed.dayOfMonth() != dom) {
                continue;
            }
            if (!repo.tryClaimDuePosting(OWNER, "fixed", fixed.id(), month)) {
                continue;
            }
            repo.insertTx(new FinanceModels.Tx(
                    null, OWNER, "fixed", fixed.amountMinor(), fixed.currency(), null,
                    fixed.name(), day, null, null, null, fixed.id(), null));
        }
        for (var saving : repo.savings(OWNER)) {
            if (saving.monthlyAmountMinor() <= 0 || saving.dayOfMonth() != dom) {
                continue;
            }
            if (!repo.tryClaimDuePosting(OWNER, "saving", saving.id(), month)) {
                continue;
            }
            repo.insertTx(new FinanceModels.Tx(
                    null, OWNER, "saving", saving.monthlyAmountMinor(), saving.currency(), null,
                    saving.name(), day, null, saving.id(), null, null, null));
        }
        for (var loan : repo.loans(OWNER)) {
            if (loan.dayOfMonth() != dom) {
                continue;
            }
            if (!repo.tryClaimDuePosting(OWNER, "loan_payment", loan.id(), month)) {
                continue;
            }
            long remaining = remainingAll(loan);
            long pay = Math.min(loan.monthlyPaymentMinor(), remaining);
            if (pay < 1) {
                continue;
            }
            repo.insertTx(new FinanceModels.Tx(
                    null, OWNER, "loan_payment", pay, loan.currency(), null,
                    loan.name(), day, null, null, loan.id(), null, null));
        }
    }

    private long remainingAll(FinanceModels.Loan loan) {
        long paid = repo.sumLoanPayments(OWNER, loan.id(), loan.currency());
        return Math.max(0, loan.principalMinor() - paid);
    }
}
