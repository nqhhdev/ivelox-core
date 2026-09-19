package com.ivelox.core.modules.finance.domain.model;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public final class FinanceMath {

    public record Tx(String kind, long amountMinor, LocalDate occurredOn, String currency) {
    }

    public record MonthSlice(
            long pool,
            long income,
            long fixed,
            long saving,
            long loanPayment,
            long loanDisbursement,
            long dailyMonth,
            long dailyToday,
            long todaySlot,
            long todayLeft,
            long remainingMonth
    ) {
    }

    private FinanceMath() {
    }

    public static MonthSlice compute(List<Tx> txs, String currency, YearMonth month, LocalDate today) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        LocalDate d = today;
        if (d.isBefore(start) || d.isAfter(end)) {
            d = end;
        }
        long income = 0;
        long fixed = 0;
        long saving = 0;
        long loanPayment = 0;
        long loanDisbursement = 0;
        long dailyMonth = 0;
        long dailyToday = 0;
        for (Tx tx : txs) {
            if (!currency.equals(tx.currency())) {
                continue;
            }
            if (tx.occurredOn().isBefore(start) || tx.occurredOn().isAfter(end)) {
                continue;
            }
            switch (tx.kind()) {
                case "income" -> income += tx.amountMinor();
                case "fixed" -> fixed += tx.amountMinor();
                case "saving" -> saving += tx.amountMinor();
                case "loan_payment" -> loanPayment += tx.amountMinor();
                case "loan_disbursement" -> loanDisbursement += tx.amountMinor();
                case "daily" -> {
                    dailyMonth += tx.amountMinor();
                    if (tx.occurredOn().equals(d)) {
                        dailyToday += tx.amountMinor();
                    }
                }
                default -> {
                }
            }
        }
        long pool = income + loanDisbursement - fixed - saving - loanPayment;
        long dailyBefore = dailyMonth - dailyToday;
        long rest = pool - dailyBefore;
        int daysInclToday = Math.max(1, end.getDayOfMonth() - d.getDayOfMonth() + 1);
        long todaySlot = Math.floorDiv(rest, daysInclToday);
        long todayLeft = todaySlot - dailyToday;
        long remainingMonth = pool - dailyMonth;
        return new MonthSlice(
                pool, income, fixed, saving, loanPayment, loanDisbursement,
                dailyMonth, dailyToday, todaySlot, todayLeft, remainingMonth
        );
    }

    public static long remainingLoan(long principalMinor, List<Tx> payments) {
        long paid = 0;
        for (Tx tx : payments) {
            if ("loan_payment".equals(tx.kind())) {
                paid += tx.amountMinor();
            }
        }
        return Math.max(0, principalMinor - paid);
    }
}
