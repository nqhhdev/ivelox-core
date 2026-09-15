package com.ivelox.core.finance;

import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.stereotype.Component;

@Component
public class FinanceDuePoster {

    static final String OWNER = "owner";

    private final FinanceRepository repo;

    public FinanceDuePoster(FinanceRepository repo) {
        this.repo = repo;
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
            if (repo.postedThisMonth(OWNER, "income", income.id(), month, "income_id")) {
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
            if (repo.postedThisMonth(OWNER, "fixed", fixed.id(), month, "fixed_expense_id")) {
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
            if (repo.postedThisMonth(OWNER, "saving", saving.id(), month, "saving_goal_id")) {
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
            long remaining = remainingAll(loan);
            if (remaining <= 0) {
                continue;
            }
            if (repo.postedThisMonth(OWNER, "loan_payment", loan.id(), month, "loan_id")) {
                continue;
            }
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
        // payments can span months — scan via listTx wide range
        var all = repo.listTx(OWNER, LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1),
                "loan_payment", null, loan.currency(), 10_000, null, null);
        long paid = 0;
        for (var t : all) {
            if (loan.id().equals(t.loanId())) {
                paid += t.amountMinor();
            }
        }
        return Math.max(0, loan.principalMinor() - paid);
    }
}
