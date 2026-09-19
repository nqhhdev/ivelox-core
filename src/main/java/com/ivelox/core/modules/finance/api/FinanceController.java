package com.ivelox.core.modules.finance.api;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.ivelox.core.config.IveloxProperties;
import com.ivelox.core.health.CivilDay;
import com.ivelox.core.modules.finance.api.FinanceDtos.Dashboard;
import com.ivelox.core.modules.finance.api.FinanceDtos.FixedView;
import com.ivelox.core.modules.finance.api.FinanceDtos.FixedWrite;
import com.ivelox.core.modules.finance.api.FinanceDtos.IncomeView;
import com.ivelox.core.modules.finance.api.FinanceDtos.IncomeWrite;
import com.ivelox.core.modules.finance.api.FinanceDtos.ItemList;
import com.ivelox.core.modules.finance.api.FinanceDtos.LoanView;
import com.ivelox.core.modules.finance.api.FinanceDtos.LoanWrite;
import com.ivelox.core.modules.finance.api.FinanceDtos.SavingView;
import com.ivelox.core.modules.finance.api.FinanceDtos.SavingWrite;
import com.ivelox.core.modules.finance.api.FinanceDtos.SettingsView;
import com.ivelox.core.modules.finance.api.FinanceDtos.SettingsWrite;
import com.ivelox.core.modules.finance.api.FinanceDtos.TxCreate;
import com.ivelox.core.modules.finance.api.FinanceDtos.TxPage;
import com.ivelox.core.modules.finance.api.FinanceDtos.TxView;
import com.ivelox.core.modules.finance.application.port.in.FinanceUseCase;
import com.ivelox.core.modules.finance.domain.model.FinanceCatalog;

@RestController
@RequestMapping("/api/v1/finance")
public class FinanceController {

    private final IveloxProperties props;
    private final FinanceUseCase service;

    public FinanceController(IveloxProperties props, FinanceUseCase service) {
        this.props = props;
        this.service = service;
    }

    @GetMapping("/currencies")
    public ItemList<FinanceCatalog.CurrencyInfo> currencies(Authentication auth) {
        requireFeature();
        ownerId(auth);
        return new ItemList<>(service.currencies());
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(
            Authentication auth,
            @RequestParam(value = "month", required = false) String month,
            @RequestParam(value = "currency", required = false) String currency
    ) {
        requireFeature();
        return service.dashboard(ownerId(auth), month, currency);
    }

    @PostMapping("/transactions")
    public ResponseEntity<TxView> createTx(
            Authentication auth,
            @RequestBody TxCreate req
    ) {
        requireFeature();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTx(ownerId(auth), req));
    }

    @GetMapping("/transactions")
    public TxPage listTx(
            Authentication auth,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "kind", required = false) String kind,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "currency", required = false) String currency,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "cursor", required = false) String cursor
    ) {
        requireFeature();
        return service.listTx(ownerId(auth), parseOptionalDate(from), parseOptionalDate(to),
                kind, category, currency, limit, cursor);
    }

    @DeleteMapping("/transactions/{id}")
    public ResponseEntity<Void> deleteTx(Authentication auth, @PathVariable("id") String id) {
        requireFeature();
        service.deleteTx(ownerId(auth), parseUuid(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/incomes")
    public List<IncomeView> listIncomes(Authentication auth) {
        requireFeature();
        return service.listIncomes(ownerId(auth));
    }

    @PostMapping("/incomes")
    public ResponseEntity<IncomeView> createIncome(
            Authentication auth, @RequestBody IncomeWrite req
    ) {
        requireFeature();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createIncome(ownerId(auth), req));
    }

    @PatchMapping("/incomes/{id}")
    public IncomeView updateIncome(
            Authentication auth, @PathVariable("id") String id, @RequestBody IncomeWrite req
    ) {
        requireFeature();
        return service.updateIncome(ownerId(auth), parseUuid(id), req);
    }

    @DeleteMapping("/incomes/{id}")
    public ResponseEntity<Void> deleteIncome(Authentication auth, @PathVariable("id") String id) {
        requireFeature();
        service.deleteIncome(ownerId(auth), parseUuid(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/savings")
    public List<SavingView> listSavings(Authentication auth) {
        requireFeature();
        return service.listSavings(ownerId(auth));
    }

    @PostMapping("/savings")
    public ResponseEntity<SavingView> createSaving(
            Authentication auth, @RequestBody SavingWrite req
    ) {
        requireFeature();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createSaving(ownerId(auth), req));
    }

    @PatchMapping("/savings/{id}")
    public SavingView updateSaving(
            Authentication auth, @PathVariable("id") String id, @RequestBody SavingWrite req
    ) {
        requireFeature();
        String userId = ownerId(auth);
        return service.updateSaving(userId, parseUuid(id), req);
    }

    @DeleteMapping("/savings/{id}")
    public ResponseEntity<Void> deleteSaving(Authentication auth, @PathVariable("id") String id) {
        requireFeature();
        service.deleteSaving(ownerId(auth), parseUuid(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/loans")
    public List<LoanView> listLoans(Authentication auth) {
        requireFeature();
        return service.listLoans(ownerId(auth));
    }

    @PostMapping("/loans")
    public ResponseEntity<LoanView> createLoan(
            Authentication auth, @RequestBody LoanWrite req
    ) {
        requireFeature();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createLoan(ownerId(auth), req));
    }

    @PatchMapping("/loans/{id}")
    public LoanView updateLoan(
            Authentication auth, @PathVariable("id") String id, @RequestBody LoanWrite req
    ) {
        requireFeature();
        String userId = ownerId(auth);
        return service.updateLoan(userId, parseUuid(id), req);
    }

    @DeleteMapping("/loans/{id}")
    public ResponseEntity<Void> deleteLoan(Authentication auth, @PathVariable("id") String id) {
        requireFeature();
        service.deleteLoan(ownerId(auth), parseUuid(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/fixed")
    public List<FixedView> listFixed(Authentication auth) {
        requireFeature();
        return service.listFixed(ownerId(auth));
    }

    @PostMapping("/fixed")
    public ResponseEntity<FixedView> createFixed(
            Authentication auth, @RequestBody FixedWrite req
    ) {
        requireFeature();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createFixed(ownerId(auth), req));
    }

    @PatchMapping("/fixed/{id}")
    public FixedView updateFixed(
            Authentication auth, @PathVariable("id") String id, @RequestBody FixedWrite req
    ) {
        requireFeature();
        return service.updateFixed(ownerId(auth), parseUuid(id), req);
    }

    @DeleteMapping("/fixed/{id}")
    public ResponseEntity<Void> deleteFixed(Authentication auth, @PathVariable("id") String id) {
        requireFeature();
        service.deleteFixed(ownerId(auth), parseUuid(id));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/settings")
    public SettingsView getSettings(Authentication auth) {
        requireFeature();
        return service.getSettings(ownerId(auth));
    }

    @PutMapping("/settings")
    public SettingsView updateSettings(
            Authentication auth, @RequestBody SettingsWrite req
    ) {
        requireFeature();
        return service.updateSettings(ownerId(auth), req);
    }

    private void requireFeature() {
        if (!props.financeEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "finance feature disabled");
        }
    }

    private static String ownerId(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid user");
        }
        return auth.getName();
    }

    private static UUID parseUuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid id");
        }
    }

    private static LocalDate parseOptionalDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return CivilDay.parse(raw);
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date must be YYYY-MM-DD");
        }
    }
}
