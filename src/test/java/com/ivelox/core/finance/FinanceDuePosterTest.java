package com.ivelox.core.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import(FinanceTestSupport.class)
@Transactional
class FinanceDuePosterTest {

    private static final String OWNER = "owner";

    @Autowired
    private FinanceRepository repo;

    @Autowired
    private FinanceDuePoster duePoster;

    @Test
    void eurMonthlyIncomePostsInEur() {
        var income = repo.insertIncome(new FinanceModels.Income(
                null, OWNER, "EUR salary", 300_000, "EUR", 15, "monthly", null));

        LocalDate due = LocalDate.of(2026, 9, 15);
        duePoster.postForDay(due);

        var txs = repo.txsInMonth(OWNER, YearMonth.from(due));
        var posted = txs.stream().filter(t -> income.id().equals(t.incomeId())).toList();
        assertEquals(1, posted.size());
        assertEquals("EUR", posted.get(0).currency());
        assertEquals(300_000, posted.get(0).amountMinor());
    }

    @Test
    void duePosterIsIdempotentForSameMonth() {
        repo.insertIncome(new FinanceModels.Income(
                null, OWNER, "Rent income", 500_000, "USD", 10, "monthly", null));

        LocalDate due = LocalDate.of(2026, 9, 10);
        duePoster.postForDay(due);
        duePoster.postForDay(due);

        var txs = repo.txsInMonth(OWNER, YearMonth.from(due)).stream()
                .filter(t -> "income".equals(t.kind()) && "USD".equals(t.currency())
                        && t.amountMinor() == 500_000)
                .toList();
        assertEquals(1, txs.size());
    }

    @Test
    void catchUpPostsDuesMissedDuringDowntime() {
        repo.insertFixed(new FinanceModels.FixedExpense(
                null, OWNER, "Rent", 1_000_000, "VND", 5, null));

        // Simulate the app having last run on 2026-09-01, then a gap until 2026-09-06 (rent due on the 5th).
        repo.advanceLastPostedOn(OWNER, LocalDate.of(2026, 9, 1));
        duePoster.catchUpTo(LocalDate.of(2026, 9, 6));

        var txs = repo.txsInMonth(OWNER, YearMonth.of(2026, 9)).stream()
                .filter(t -> "fixed".equals(t.kind()))
                .toList();
        assertEquals(1, txs.size(), "the missed due day should be caught up, not skipped");
        assertEquals(LocalDate.of(2026, 9, 5), txs.get(0).occurredOn());
    }

    @Test
    void catchUpDoesNotDuplicateAlreadyPostedDays() {
        repo.insertFixed(new FinanceModels.FixedExpense(
                null, OWNER, "Rent", 1_000_000, "VND", 5, null));

        duePoster.catchUpTo(LocalDate.of(2026, 9, 5));
        duePoster.catchUpTo(LocalDate.of(2026, 9, 6));

        var txs = repo.txsInMonth(OWNER, YearMonth.of(2026, 9)).stream()
                .filter(t -> "fixed".equals(t.kind()))
                .toList();
        assertEquals(1, txs.size());
    }

    @Test
    void loanPaymentSkippedOnceFullyRepaid() {
        var loan = repo.insertLoan(new FinanceModels.Loan(
                null, OWNER, "Small loan", 100_000, 100_000, "USD", 5, "", null));

        LocalDate firstDue = LocalDate.of(2026, 9, 5);
        duePoster.postForDay(firstDue);

        var afterFirst = repo.txsInMonth(OWNER, YearMonth.from(firstDue)).stream()
                .filter(t -> loan.id().equals(t.loanId()))
                .toList();
        assertEquals(1, afterFirst.size());
        assertEquals(100_000, afterFirst.get(0).amountMinor());

        LocalDate nextDue = LocalDate.of(2026, 10, 5);
        duePoster.postForDay(nextDue);

        var afterSecond = repo.txsInMonth(OWNER, YearMonth.from(nextDue)).stream()
                .filter(t -> loan.id().equals(t.loanId()))
                .toList();
        assertTrue(afterSecond.isEmpty(), "loan is fully repaid, no further auto-payment expected");
    }
}
