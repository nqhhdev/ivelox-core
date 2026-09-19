package com.ivelox.core.modules.finance.application.port.out;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ivelox.core.modules.finance.domain.model.FixedExpense;
import com.ivelox.core.modules.finance.domain.model.Income;
import com.ivelox.core.modules.finance.domain.model.Loan;
import com.ivelox.core.modules.finance.domain.model.SavingGoal;
import com.ivelox.core.modules.finance.domain.model.Settings;
import com.ivelox.core.modules.finance.domain.model.Tx;

public interface FinanceRepositoryPort {

    Settings settings(String userId);

    void saveSettings(Settings s);

    Tx insertTx(Tx tx);

    List<Tx> txsInMonth(String userId, YearMonth month);

    List<Tx> listTx(
            String userId, LocalDate from, LocalDate to, String kind, String category, String currency,
            int limit, LocalDate cursorDay, UUID cursorId
    );

    Optional<Tx> findTx(String userId, UUID id);

    int deleteTx(String userId, UUID id);

    long sumLoanPayments(String userId, UUID loanId, String currency);

    long sumSavingContributions(String userId, UUID goalId, String currency);

    /**
     * Atomically claims a monthly due posting slot. Returns true if this call claimed it
     * (caller should post the transaction), false if it was already claimed (already posted).
     */
    boolean tryClaimDuePosting(String userId, String kind, UUID sourceId, YearMonth period);

    Optional<LocalDate> lastPostedOn(String userId);

    void advanceLastPostedOn(String userId, LocalDate day);

    List<Income> incomes(String userId);

    Optional<Income> findIncome(String userId, UUID id);

    Income insertIncome(Income row);

    void updateIncome(Income row);

    int deleteIncome(String userId, UUID id);

    List<SavingGoal> savings(String userId);

    Optional<SavingGoal> findSaving(String userId, UUID id);

    SavingGoal insertSaving(SavingGoal row);

    void updateSaving(SavingGoal row);

    int deleteSaving(String userId, UUID id);

    List<Loan> loans(String userId);

    Optional<Loan> findLoan(String userId, UUID id);

    Loan insertLoan(Loan row);

    void updateLoan(Loan row);

    int deleteLoan(String userId, UUID id);

    List<FixedExpense> fixed(String userId);

    Optional<FixedExpense> findFixed(String userId, UUID id);

    FixedExpense insertFixed(FixedExpense row);

    void updateFixed(FixedExpense row);

    int deleteFixed(String userId, UUID id);

    List<String> currenciesInUse(String userId);

    boolean insertNotifyIfAbsent(String userId, String type, LocalDate civilDay, String currency);

    /** Removes a reserved dedup record after a failed send, so a later retry is not permanently blocked. */
    void deleteNotifyLog(String userId, String type, LocalDate civilDay, String currency);
}
