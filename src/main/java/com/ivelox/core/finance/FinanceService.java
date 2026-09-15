package com.ivelox.core.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.ivelox.core.health.CivilDay;

@Service
public class FinanceService {

    private static final String OWNER = "owner";

    private final FinanceRepository repo;
    private final FinanceDuePoster duePoster;
    private final FinanceNotifier notifier;

    public FinanceService(FinanceRepository repo, FinanceDuePoster duePoster, FinanceNotifier notifier) {
        this.repo = repo;
        this.duePoster = duePoster;
        this.notifier = notifier;
    }

    public List<FinanceCatalog.CurrencyInfo> currencies() {
        return FinanceCatalog.currencies();
    }

    // ---------------------------------------------------------------- dashboard

    public FinanceModels.Dashboard dashboard(String userId, String monthParam, String currencyParam) {
        duePoster.postForDay(CivilDay.todayIct());

        var settings = repo.settings(userId);
        YearMonth month = parseMonth(monthParam);
        String currency = currencyParam == null || currencyParam.isBlank()
                ? settings.homeCurrency()
                : FinanceMoney.requireCurrency(currencyParam);

        LocalDate today = CivilDay.todayIct();
        boolean isCurrentMonth = month.equals(YearMonth.from(today));

        var txs = repo.txsInMonth(userId, month).stream()
                .map(t -> new FinanceMath.Tx(t.kind(), t.amountMinor(), t.occurredOn(), t.currency()))
                .toList();
        var slice = FinanceMath.compute(txs, currency, month, today);

        YearMonth prevMonth = month.minusMonths(1);
        var prevTxs = repo.txsInMonth(userId, prevMonth).stream()
                .map(t -> new FinanceMath.Tx(t.kind(), t.amountMinor(), t.occurredOn(), t.currency()))
                .toList();
        var prevSlice = FinanceMath.compute(prevTxs, currency, prevMonth, prevMonth.atEndOfMonth());

        var savings = repo.savings(userId).stream()
                .filter(s -> currency.equals(s.currency()))
                .map(s -> toSavingView(userId, s))
                .toList();
        var loans = repo.loans(userId).stream()
                .filter(l -> currency.equals(l.currency()))
                .map(l -> toLoanView(userId, l))
                .toList();
        var upcoming = upcoming(userId, today, currency);

        String monthStr = month.toString();
        return new FinanceModels.Dashboard(
                monthStr,
                today.toString(),
                isCurrentMonth,
                currency,
                settings.homeCurrency(),
                repo.currenciesInUse(userId),
                money(slice.pool(), currency),
                money(slice.income(), currency),
                money(slice.fixed(), currency),
                money(slice.saving(), currency),
                money(slice.loanPayment(), currency),
                money(slice.loanDisbursement(), currency),
                money(slice.dailyMonth(), currency),
                money(slice.dailyToday(), currency),
                money(slice.todaySlot(), currency),
                money(slice.todayLeft(), currency),
                money(slice.remainingMonth(), currency),
                money(prevSlice.remainingMonth(), currency),
                savings,
                loans,
                upcoming
        );
    }

    private List<FinanceModels.Upcoming> upcoming(String userId, LocalDate today, String currency) {
        LocalDate horizon = today.plusDays(7);
        List<FinanceModels.Upcoming> out = new ArrayList<>();
        for (var fixed : repo.fixed(userId)) {
            if (!currency.equals(fixed.currency())) {
                continue;
            }
            dueOnOrAfter(today, horizon, fixed.dayOfMonth()).ifPresent(due ->
                    out.add(new FinanceModels.Upcoming("fixed", fixed.name(), due, money(fixed.amountMinor(), currency))));
        }
        for (var income : repo.incomes(userId)) {
            if (!"monthly".equals(income.recurrence()) || !currency.equals(income.currency())) {
                continue;
            }
            dueOnOrAfter(today, horizon, income.dayOfMonth()).ifPresent(due ->
                    out.add(new FinanceModels.Upcoming("income", income.name(), due, money(income.amountMinor(), currency))));
        }
        for (var loan : repo.loans(userId)) {
            if (!currency.equals(loan.currency())) {
                continue;
            }
            long remaining = FinanceMath.remainingLoan(loan.principalMinor(), allLoanPayments(loan));
            if (remaining <= 0) {
                continue;
            }
            dueOnOrAfter(today, horizon, loan.dayOfMonth()).ifPresent(due ->
                    out.add(new FinanceModels.Upcoming("loan_payment", loan.name(), due,
                            money(Math.min(loan.monthlyPaymentMinor(), remaining), currency))));
        }
        for (var saving : repo.savings(userId)) {
            if (saving.monthlyAmountMinor() <= 0 || !currency.equals(saving.currency())) {
                continue;
            }
            dueOnOrAfter(today, horizon, saving.dayOfMonth()).ifPresent(due ->
                    out.add(new FinanceModels.Upcoming("saving", saving.name(), due, money(saving.monthlyAmountMinor(), currency))));
        }
        return out.stream()
                .sorted((a, b) -> a.dueOn().compareTo(b.dueOn()))
                .toList();
    }

    private Optional<LocalDate> dueOnOrAfter(LocalDate today, LocalDate horizon, int dayOfMonth) {
        LocalDate candidate = today.withDayOfMonth(Math.min(dayOfMonth, today.lengthOfMonth()));
        if (candidate.isBefore(today)) {
            YearMonth next = YearMonth.from(today).plusMonths(1);
            candidate = next.atDay(Math.min(dayOfMonth, next.lengthOfMonth()));
        }
        return candidate.isAfter(horizon) ? Optional.empty() : Optional.of(candidate);
    }

    private List<FinanceMath.Tx> allLoanPayments(FinanceModels.Loan loan) {
        return repo.listTx(OWNER, LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1),
                        "loan_payment", null, loan.currency(), 10_000, null, null)
                .stream()
                .filter(t -> loan.id().equals(t.loanId()))
                .map(t -> new FinanceMath.Tx(t.kind(), t.amountMinor(), t.occurredOn(), t.currency()))
                .toList();
    }

    private FinanceModels.SavingView toSavingView(String userId, FinanceModels.SavingGoal s) {
        long contributed = repo.listTx(userId, LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1),
                        "saving", null, s.currency(), 10_000, null, null)
                .stream()
                .filter(t -> s.id().equals(t.savingGoalId()))
                .mapToLong(FinanceModels.Tx::amountMinor)
                .sum();
        return new FinanceModels.SavingView(
                s.id().toString(), s.name(), s.currency(),
                money(s.targetAmountMinor(), s.currency()),
                money(contributed, s.currency()),
                money(s.monthlyAmountMinor(), s.currency()),
                s.dayOfMonth()
        );
    }

    private FinanceModels.LoanView toLoanView(String userId, FinanceModels.Loan l) {
        long remaining = FinanceMath.remainingLoan(l.principalMinor(), allLoanPayments(l));
        return new FinanceModels.LoanView(
                l.id().toString(), l.name(), l.currency(),
                money(l.principalMinor(), l.currency()),
                money(remaining, l.currency()),
                money(l.monthlyPaymentMinor(), l.currency()),
                l.dayOfMonth(),
                l.note()
        );
    }

    // ---------------------------------------------------------------- transactions

    public FinanceModels.TxView createTx(String userId, FinanceModels.TxCreate req) {
        if (!FinanceCatalog.isKind(req.kind())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_kind");
        }
        var settings = repo.settings(userId);
        String currency = req.currency() == null || req.currency().isBlank()
                ? settings.homeCurrency()
                : FinanceMoney.requireCurrency(req.currency());
        long amountMinor = FinanceMoney.toMinor(req.amount(), currency);
        LocalDate occurredOn = req.occurredOn() != null ? req.occurredOn() : CivilDay.todayIct();

        String category = null;
        UUID incomeId = null;
        UUID savingGoalId = null;
        UUID loanId = null;
        UUID fixedExpenseId = null;

        switch (req.kind()) {
            case "daily" -> {
                if (!FinanceCatalog.isCategory(req.category())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_category");
                }
                category = req.category();
            }
            case "saving" -> {
                if (req.savingGoalId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing_link");
                }
                var goal = repo.findSaving(req.savingGoalId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
                requireSameCurrency(goal.currency(), currency);
                savingGoalId = goal.id();
            }
            case "loan_payment", "loan_disbursement" -> {
                if (req.loanId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing_link");
                }
                var loan = repo.findLoan(req.loanId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
                requireSameCurrency(loan.currency(), currency);
                loanId = loan.id();
            }
            case "fixed" -> {
                if (req.fixedExpenseId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing_link");
                }
                var fixed = repo.findFixed(req.fixedExpenseId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
                requireSameCurrency(fixed.currency(), currency);
                fixedExpenseId = fixed.id();
            }
            case "income" -> incomeId = req.incomeId();
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_kind");
        }

        var saved = repo.insertTx(new FinanceModels.Tx(
                null, userId, req.kind(), amountMinor, currency, category, req.note(),
                occurredOn, incomeId, savingGoalId, loanId, fixedExpenseId, null
        ));

        YearMonth month = YearMonth.from(occurredOn);
        var txs = repo.txsInMonth(userId, month).stream()
                .map(t -> new FinanceMath.Tx(t.kind(), t.amountMinor(), t.occurredOn(), t.currency()))
                .toList();
        var slice = FinanceMath.compute(txs, currency, month, CivilDay.todayIct());
        notifier.afterPost(slice, currency, req.kind(), settings);

        return FinanceModels.TxView.of(saved);
    }

    public FinanceModels.TxPage listTx(
            String userId, LocalDate from, LocalDate to, String kind, String category, String currency,
            int limit, String cursor
    ) {
        LocalDate f = from != null ? from : YearMonth.from(CivilDay.todayIct()).atDay(1);
        LocalDate t = to != null ? to : CivilDay.todayIct();
        int lim = Math.min(Math.max(limit, 1), 200);

        LocalDate cursorDay = null;
        UUID cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor));
            String[] parts = decoded.split("\\|", 2);
            cursorDay = LocalDate.parse(parts[0]);
            cursorId = UUID.fromString(parts[1]);
        }

        var rows = repo.listTx(userId, f, t, kind, category, currency, lim + 1, cursorDay, cursorId);
        String nextCursor = null;
        if (rows.size() > lim) {
            var last = rows.get(lim - 1);
            rows = rows.subList(0, lim);
            nextCursor = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString((last.occurredOn() + "|" + last.id()).getBytes());
        }
        return new FinanceModels.TxPage(rows.stream().map(FinanceModels.TxView::of).toList(), nextCursor);
    }

    public void deleteTx(UUID id) {
        var tx = repo.findTx(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
        repo.deleteTx(tx.id());
    }

    // ---------------------------------------------------------------- setup CRUD

    public List<FinanceModels.IncomeView> listIncomes(String userId) {
        return repo.incomes(userId).stream().map(FinanceModels.IncomeView::of).toList();
    }

    public FinanceModels.IncomeView createIncome(String userId, FinanceModels.IncomeWrite req) {
        requireName(req.name());
        int dayOfMonth = requireDay(req.dayOfMonth());
        if (!"none".equals(req.recurrence()) && !"monthly".equals(req.recurrence())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_recurrence");
        }
        var settings = repo.settings(userId);
        String currency = req.currency() == null || req.currency().isBlank()
                ? settings.homeCurrency()
                : FinanceMoney.requireCurrency(req.currency());
        long amountMinor = FinanceMoney.toMinor(req.amount(), currency);

        var saved = repo.insertIncome(new FinanceModels.Income(
                null, userId, req.name(), amountMinor, currency, dayOfMonth, req.recurrence(), null
        ));
        if ("none".equals(req.recurrence())) {
            repo.insertTx(new FinanceModels.Tx(
                    null, userId, "income", amountMinor, currency, null, saved.name(),
                    CivilDay.todayIct(), saved.id(), null, null, null, null
            ));
        }
        return FinanceModels.IncomeView.of(saved);
    }

    public FinanceModels.IncomeView updateIncome(UUID id, FinanceModels.IncomeWrite req) {
        var existing = repo.findIncome(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
        String name = req.name() != null ? req.name() : existing.name();
        int dayOfMonth = req.dayOfMonth() != null ? requireDay(req.dayOfMonth()) : existing.dayOfMonth();
        String recurrence = req.recurrence() != null ? req.recurrence() : existing.recurrence();
        long amountMinor = req.amount() != null ? FinanceMoney.toMinor(req.amount(), existing.currency()) : existing.amountMinor();
        var updated = new FinanceModels.Income(existing.id(), existing.userId(), name, amountMinor,
                existing.currency(), dayOfMonth, recurrence, existing.createdAt());
        repo.updateIncome(updated);
        return FinanceModels.IncomeView.of(updated);
    }

    public void deleteIncome(UUID id) {
        if (repo.deleteIncome(id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found");
        }
    }

    public List<FinanceModels.SavingView> listSavings(String userId) {
        return repo.savings(userId).stream().map(s -> toSavingView(userId, s)).toList();
    }

    public FinanceModels.SavingView createSaving(String userId, FinanceModels.SavingWrite req) {
        requireName(req.name());
        int dayOfMonth = requireDay(req.dayOfMonth());
        var settings = repo.settings(userId);
        String currency = req.currency() == null || req.currency().isBlank()
                ? settings.homeCurrency()
                : FinanceMoney.requireCurrency(req.currency());
        long targetMinor = FinanceMoney.toMinor(req.targetAmount(), currency);
        long monthlyMinor = req.monthlyAmount() == null || req.monthlyAmount().signum() == 0
                ? 0
                : FinanceMoney.toMinor(req.monthlyAmount(), currency);

        var saved = repo.insertSaving(new FinanceModels.SavingGoal(
                null, userId, req.name(), targetMinor, monthlyMinor, currency, dayOfMonth, null
        ));
        return toSavingView(userId, saved);
    }

    public FinanceModels.SavingView updateSaving(String userId, UUID id, FinanceModels.SavingWrite req) {
        var existing = repo.findSaving(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
        String name = req.name() != null ? req.name() : existing.name();
        int dayOfMonth = req.dayOfMonth() != null ? requireDay(req.dayOfMonth()) : existing.dayOfMonth();
        long targetMinor = req.targetAmount() != null ? FinanceMoney.toMinor(req.targetAmount(), existing.currency()) : existing.targetAmountMinor();
        long monthlyMinor = req.monthlyAmount() != null
                ? (req.monthlyAmount().signum() == 0 ? 0 : FinanceMoney.toMinor(req.monthlyAmount(), existing.currency()))
                : existing.monthlyAmountMinor();
        var updated = new FinanceModels.SavingGoal(existing.id(), existing.userId(), name, targetMinor,
                monthlyMinor, existing.currency(), dayOfMonth, existing.createdAt());
        repo.updateSaving(updated);
        return toSavingView(userId, updated);
    }

    public void deleteSaving(UUID id) {
        if (repo.deleteSaving(id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found");
        }
    }

    public List<FinanceModels.LoanView> listLoans(String userId) {
        return repo.loans(userId).stream().map(l -> toLoanView(userId, l)).toList();
    }

    public FinanceModels.LoanView createLoan(String userId, FinanceModels.LoanWrite req) {
        requireName(req.name());
        int dayOfMonth = requireDay(req.dayOfMonth());
        var settings = repo.settings(userId);
        String currency = req.currency() == null || req.currency().isBlank()
                ? settings.homeCurrency()
                : FinanceMoney.requireCurrency(req.currency());
        long principalMinor = FinanceMoney.toMinor(req.principal(), currency);
        long monthlyMinor = FinanceMoney.toMinor(req.monthlyPayment(), currency);

        var saved = repo.insertLoan(new FinanceModels.Loan(
                null, userId, req.name(), principalMinor, monthlyMinor, currency, dayOfMonth,
                req.note() == null ? "" : req.note(), null
        ));
        if (Boolean.TRUE.equals(req.disburseNow())) {
            repo.insertTx(new FinanceModels.Tx(
                    null, userId, "loan_disbursement", principalMinor, currency, null, saved.name(),
                    CivilDay.todayIct(), null, null, saved.id(), null, null
            ));
        }
        return toLoanView(userId, saved);
    }

    public FinanceModels.LoanView updateLoan(String userId, UUID id, FinanceModels.LoanWrite req) {
        var existing = repo.findLoan(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
        String name = req.name() != null ? req.name() : existing.name();
        int dayOfMonth = req.dayOfMonth() != null ? requireDay(req.dayOfMonth()) : existing.dayOfMonth();
        long principalMinor = req.principal() != null ? FinanceMoney.toMinor(req.principal(), existing.currency()) : existing.principalMinor();
        long monthlyMinor = req.monthlyPayment() != null ? FinanceMoney.toMinor(req.monthlyPayment(), existing.currency()) : existing.monthlyPaymentMinor();
        String note = req.note() != null ? req.note() : existing.note();
        var updated = new FinanceModels.Loan(existing.id(), existing.userId(), name, principalMinor,
                monthlyMinor, existing.currency(), dayOfMonth, note, existing.createdAt());
        repo.updateLoan(updated);
        return toLoanView(userId, updated);
    }

    public void deleteLoan(UUID id) {
        if (repo.deleteLoan(id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found");
        }
    }

    public List<FinanceModels.FixedView> listFixed(String userId) {
        return repo.fixed(userId).stream().map(FinanceModels.FixedView::of).toList();
    }

    public FinanceModels.FixedView createFixed(String userId, FinanceModels.FixedWrite req) {
        requireName(req.name());
        int dayOfMonth = requireDay(req.dayOfMonth());
        var settings = repo.settings(userId);
        String currency = req.currency() == null || req.currency().isBlank()
                ? settings.homeCurrency()
                : FinanceMoney.requireCurrency(req.currency());
        long amountMinor = FinanceMoney.toMinor(req.amount(), currency);

        var saved = repo.insertFixed(new FinanceModels.FixedExpense(
                null, userId, req.name(), amountMinor, currency, dayOfMonth, null
        ));
        return FinanceModels.FixedView.of(saved);
    }

    public FinanceModels.FixedView updateFixed(UUID id, FinanceModels.FixedWrite req) {
        var existing = repo.findFixed(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found"));
        String name = req.name() != null ? req.name() : existing.name();
        int dayOfMonth = req.dayOfMonth() != null ? requireDay(req.dayOfMonth()) : existing.dayOfMonth();
        long amountMinor = req.amount() != null ? FinanceMoney.toMinor(req.amount(), existing.currency()) : existing.amountMinor();
        var updated = new FinanceModels.FixedExpense(existing.id(), existing.userId(), name, amountMinor,
                existing.currency(), dayOfMonth, existing.createdAt());
        repo.updateFixed(updated);
        return FinanceModels.FixedView.of(updated);
    }

    public void deleteFixed(UUID id) {
        if (repo.deleteFixed(id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found");
        }
    }

    // ---------------------------------------------------------------- settings

    public FinanceModels.SettingsView getSettings(String userId) {
        var s = repo.settings(userId);
        return new FinanceModels.SettingsView(s.homeCurrency(), s.digestHour(), s.digestEnabled(),
                s.overSlotAlert(), s.overMonthAlert());
    }

    public FinanceModels.SettingsView updateSettings(String userId, FinanceModels.SettingsWrite req) {
        var existing = repo.settings(userId);
        String homeCurrency = req.homeCurrency() == null || req.homeCurrency().isBlank()
                ? existing.homeCurrency()
                : FinanceMoney.requireCurrency(req.homeCurrency());
        int digestHour = req.digestHour() != null ? req.digestHour() : existing.digestHour();
        if (digestHour < 0 || digestHour > 23) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_digest_hour");
        }
        boolean digestEnabled = req.digestEnabled() != null ? req.digestEnabled() : existing.digestEnabled();
        boolean overSlot = req.overSlotAlert() != null ? req.overSlotAlert() : existing.overSlotAlert();
        boolean overMonth = req.overMonthAlert() != null ? req.overMonthAlert() : existing.overMonthAlert();
        var updated = new FinanceModels.Settings(userId, homeCurrency, digestHour, digestEnabled, overSlot, overMonth);
        repo.saveSettings(updated);
        return new FinanceModels.SettingsView(updated.homeCurrency(), updated.digestHour(),
                updated.digestEnabled(), updated.overSlotAlert(), updated.overMonthAlert());
    }

    // ---------------------------------------------------------------- helpers

    private static FinanceMoney.MoneyDto money(long minor, String currency) {
        return FinanceMoney.MoneyDto.of(minor, currency);
    }

    private static void requireSameCurrency(String setupCurrency, String txCurrency) {
        if (!setupCurrency.equals(txCurrency)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currency_mismatch");
        }
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_name");
        }
    }

    private static int requireDay(Integer day) {
        if (day == null || day < 1 || day > 28) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_day");
        }
        return day;
    }

    private static YearMonth parseMonth(String raw) {
        if (raw == null || raw.isBlank()) {
            return YearMonth.from(CivilDay.todayIct());
        }
        try {
            return YearMonth.parse(raw.trim());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month must be YYYY-MM");
        }
    }
}
