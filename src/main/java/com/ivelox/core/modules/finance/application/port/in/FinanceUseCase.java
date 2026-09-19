package com.ivelox.core.modules.finance.application.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.ivelox.core.modules.finance.api.FinanceDtos.Dashboard;
import com.ivelox.core.modules.finance.api.FinanceDtos.FixedView;
import com.ivelox.core.modules.finance.api.FinanceDtos.FixedWrite;
import com.ivelox.core.modules.finance.api.FinanceDtos.IncomeView;
import com.ivelox.core.modules.finance.api.FinanceDtos.IncomeWrite;
import com.ivelox.core.modules.finance.api.FinanceDtos.LoanView;
import com.ivelox.core.modules.finance.api.FinanceDtos.LoanWrite;
import com.ivelox.core.modules.finance.api.FinanceDtos.SavingView;
import com.ivelox.core.modules.finance.api.FinanceDtos.SavingWrite;
import com.ivelox.core.modules.finance.api.FinanceDtos.SettingsView;
import com.ivelox.core.modules.finance.api.FinanceDtos.SettingsWrite;
import com.ivelox.core.modules.finance.api.FinanceDtos.TxCreate;
import com.ivelox.core.modules.finance.api.FinanceDtos.TxPage;
import com.ivelox.core.modules.finance.api.FinanceDtos.TxView;
import com.ivelox.core.modules.finance.domain.model.FinanceCatalog;

public interface FinanceUseCase {

    List<FinanceCatalog.CurrencyInfo> currencies();

    Dashboard dashboard(String userId, String monthParam, String currencyParam);

    TxView createTx(String userId, TxCreate req);

    TxPage listTx(
            String userId, LocalDate from, LocalDate to, String kind, String category, String currency,
            int limit, String cursor
    );

    void deleteTx(String userId, UUID id);

    List<IncomeView> listIncomes(String userId);

    IncomeView createIncome(String userId, IncomeWrite req);

    IncomeView updateIncome(String userId, UUID id, IncomeWrite req);

    void deleteIncome(String userId, UUID id);

    List<SavingView> listSavings(String userId);

    SavingView createSaving(String userId, SavingWrite req);

    SavingView updateSaving(String userId, UUID id, SavingWrite req);

    void deleteSaving(String userId, UUID id);

    List<LoanView> listLoans(String userId);

    LoanView createLoan(String userId, LoanWrite req);

    LoanView updateLoan(String userId, UUID id, LoanWrite req);

    void deleteLoan(String userId, UUID id);

    List<FixedView> listFixed(String userId);

    FixedView createFixed(String userId, FixedWrite req);

    FixedView updateFixed(String userId, UUID id, FixedWrite req);

    void deleteFixed(String userId, UUID id);

    SettingsView getSettings(String userId);

    SettingsView updateSettings(String userId, SettingsWrite req);
}
