package com.ivelox.core.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

class FinanceMathTest {

    @Test
    void septemberWorkedExampleMatchesSpec() {
        YearMonth month = YearMonth.of(2026, 9);
        LocalDate today = LocalDate.of(2026, 9, 12);
        List<FinanceMath.Tx> txs = List.of(
                new FinanceMath.Tx("income", 20_000_000, LocalDate.of(2026, 9, 1), "VND"),
                new FinanceMath.Tx("fixed", 6_000_000, LocalDate.of(2026, 9, 1), "VND"),
                new FinanceMath.Tx("saving", 2_000_000, LocalDate.of(2026, 9, 1), "VND"),
                new FinanceMath.Tx("loan_payment", 3_000_000, LocalDate.of(2026, 9, 1), "VND"),
                new FinanceMath.Tx("daily", 1_800_000, LocalDate.of(2026, 9, 11), "VND"),
                new FinanceMath.Tx("daily", 220_000, LocalDate.of(2026, 9, 12), "VND")
        );

        var slice = FinanceMath.compute(txs, "VND", month, today);

        assertEquals(9_000_000, slice.pool());
        assertEquals(2_020_000, slice.dailyMonth());
        assertEquals(220_000, slice.dailyToday());
        assertEquals(378_947, slice.todaySlot());
        assertEquals(158_947, slice.todayLeft());
        assertEquals(6_980_000, slice.remainingMonth());
    }

    @Test
    void otherCurrencyTransactionsAreIgnored() {
        YearMonth month = YearMonth.of(2026, 9);
        LocalDate today = LocalDate.of(2026, 9, 12);
        List<FinanceMath.Tx> txs = List.of(
                new FinanceMath.Tx("income", 20_000_000, LocalDate.of(2026, 9, 1), "VND"),
                new FinanceMath.Tx("daily", 450, LocalDate.of(2026, 9, 12), "USD")
        );

        var slice = FinanceMath.compute(txs, "VND", month, today);
        assertEquals(20_000_000, slice.pool());
        assertEquals(0, slice.dailyMonth());
    }

    @Test
    void lastDayOfMonthUsedWhenViewingPastMonth() {
        YearMonth month = YearMonth.of(2026, 8);
        LocalDate today = LocalDate.of(2026, 9, 12);
        List<FinanceMath.Tx> txs = List.of(
                new FinanceMath.Tx("income", 1000, LocalDate.of(2026, 8, 1), "USD"),
                new FinanceMath.Tx("daily", 100, LocalDate.of(2026, 8, 31), "USD")
        );

        var slice = FinanceMath.compute(txs, "USD", month, today);
        assertEquals(900, slice.remainingMonth());
        assertEquals(100, slice.dailyToday());
        assertEquals(1, slice.todaySlot() >= 0 ? 1 : 0);
    }

    @Test
    void emptyMonthYieldsZeroedSlice() {
        YearMonth month = YearMonth.of(2026, 9);
        LocalDate today = LocalDate.of(2026, 9, 12);
        var slice = FinanceMath.compute(List.of(), "VND", month, today);

        assertEquals(0, slice.pool());
        assertEquals(0, slice.dailyMonth());
        assertEquals(0, slice.dailyToday());
        assertEquals(0, slice.todaySlot());
        assertEquals(0, slice.todayLeft());
        assertEquals(0, slice.remainingMonth());
    }

    @Test
    void usdCentsExamplePoolMatchesSpec() {
        YearMonth month = YearMonth.of(2026, 9);
        LocalDate today = LocalDate.of(2026, 9, 12);
        List<FinanceMath.Tx> txs = List.of(
                new FinanceMath.Tx("income", 10_000, LocalDate.of(2026, 9, 1), "USD")
        );

        var slice = FinanceMath.compute(txs, "USD", month, today);
        assertEquals(10_000, slice.pool());
        assertEquals(10_000, slice.remainingMonth());
    }

    @Test
    void todaySlotUsesFloorDivisionWhenOverBudget() {
        // pool = -1, 2 days left (29th and 30th of September): mathematical floor(-1/2) = -1,
        // not truncated-toward-zero 0.
        YearMonth month = YearMonth.of(2026, 9);
        LocalDate today = LocalDate.of(2026, 9, 29);
        List<FinanceMath.Tx> txs = List.of(
                new FinanceMath.Tx("income", 0, LocalDate.of(2026, 9, 1), "USD"),
                new FinanceMath.Tx("fixed", 1, LocalDate.of(2026, 9, 1), "USD")
        );

        var slice = FinanceMath.compute(txs, "USD", month, today);
        assertEquals(-1, slice.pool());
        assertEquals(-1, slice.todaySlot(), "floor(-1/2) must be -1, not 0");
    }

    @Test
    void remainingLoanNeverGoesNegative() {
        List<FinanceMath.Tx> payments = List.of(
                new FinanceMath.Tx("loan_payment", 30_000_000, LocalDate.of(2026, 9, 1), "VND"),
                new FinanceMath.Tx("loan_payment", 20_000_000, LocalDate.of(2026, 10, 1), "VND")
        );
        assertEquals(0, FinanceMath.remainingLoan(40_000_000, payments));
        assertEquals(10_000_000,
                FinanceMath.remainingLoan(40_000_000, List.of(payments.get(0))));
    }
}
