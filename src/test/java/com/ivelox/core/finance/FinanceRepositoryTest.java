package com.ivelox.core.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FinanceRepositoryTest {

    @Autowired
    private FinanceRepository repo;

    @Test
    void findIncomeIsScopedToOwningUser() {
        var income = repo.insertIncome(new FinanceModels.Income(
                null, "owner", "Salary", 1000, "USD", 1, "monthly", null));

        assertTrue(repo.findIncome("owner", income.id()).isPresent());
        assertTrue(repo.findIncome("intruder", income.id()).isEmpty(),
                "a different user_id must not be able to look up another user's income");
    }

    @Test
    void deleteIncomeIsScopedToOwningUser() {
        var income = repo.insertIncome(new FinanceModels.Income(
                null, "owner", "Salary", 1000, "USD", 1, "monthly", null));

        int deletedByIntruder = repo.deleteIncome("intruder", income.id());
        assertEquals(0, deletedByIntruder, "a different user_id must not be able to delete another user's income");
        assertTrue(repo.findIncome("owner", income.id()).isPresent(), "the row must remain untouched");
    }

    @Test
    void findAndDeleteTxAreScopedToOwningUser() {
        var tx = repo.insertTx(new FinanceModels.Tx(
                null, "owner", "daily", 500, "USD", "coffee", "", java.time.LocalDate.now(),
                null, null, null, null, null));

        assertTrue(repo.findTx("intruder", tx.id()).isEmpty());
        assertEquals(0, repo.deleteTx("intruder", tx.id()));
        assertTrue(repo.findTx("owner", tx.id()).isPresent());
    }

    @Test
    void sumLoanPaymentsIsNotLimitedByRowCount() {
        var loan = repo.insertLoan(new FinanceModels.Loan(
                null, "owner", "Big loan", 100_000_000, 1_000_000, "USD", 1, "", null));

        // Insert more than the old hardcoded 10,000-row cap to prove the aggregation isn't limited.
        int paymentsToInsert = 10_050;
        for (int i = 0; i < paymentsToInsert; i++) {
            repo.insertTx(new FinanceModels.Tx(
                    null, "owner", "loan_payment", 1, "USD",
                    null, "", java.time.LocalDate.of(2020, 1, 1).plusDays(i),
                    null, null, loan.id(), null, null));
        }

        long sum = repo.sumLoanPayments("owner", loan.id(), "USD");
        assertEquals(paymentsToInsert, sum, "every payment must be counted, not just the first 10,000");
    }
}
