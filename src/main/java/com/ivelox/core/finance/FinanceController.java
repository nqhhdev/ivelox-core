package com.ivelox.core.finance;

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

@RestController
@RequestMapping("/api/v1/finance")
public class FinanceController {

    private final IveloxProperties props;
    private final FinanceService service;

    public FinanceController(IveloxProperties props, FinanceService service) {
        this.props = props;
        this.service = service;
    }

    @GetMapping("/currencies")
    public FinanceModels.ItemList<FinanceCatalog.CurrencyInfo> currencies(Authentication auth) {
        requireFeature();
        ownerId(auth);
        return new FinanceModels.ItemList<>(service.currencies());
    }

    @GetMapping("/dashboard")
    public FinanceModels.Dashboard dashboard(
            Authentication auth,
            @RequestParam(value = "month", required = false) String month,
            @RequestParam(value = "currency", required = false) String currency
    ) {
        requireFeature();
        return service.dashboard(ownerId(auth), month, currency);
    }

    @PostMapping("/transactions")
    public ResponseEntity<FinanceModels.TxView> createTx(
            Authentication auth,
            @RequestBody FinanceModels.TxCreate req
    ) {
        requireFeature();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTx(ownerId(auth), req));
    }

    @GetMapping("/transactions")
    public FinanceModels.TxPage listTx(
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
    public List<FinanceModels.IncomeView> listIncomes(Authentication auth) {
        requireFeature();
        return service.listIncomes(ownerId(auth));
    }

    @PostMapping("/incomes")
    public ResponseEntity<FinanceModels.IncomeView> createIncome(
            Authentication auth, @RequestBody FinanceModels.IncomeWrite req
    ) {
        requireFeature();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createIncome(ownerId(auth), req));
    }

    @PatchMapping("/incomes/{id}")
    public FinanceModels.IncomeView updateIncome(
            Authentication auth, @PathVariable("id") String id, @RequestBody FinanceModels.IncomeWrite req
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
    public List<FinanceModels.SavingView> listSavings(Authentication auth) {
        requireFeature();
        return service.listSavings(ownerId(auth));
    }

    @PostMapping("/savings")
    public ResponseEntity<FinanceModels.SavingView> createSaving(
            Authentication auth, @RequestBody FinanceModels.SavingWrite req
    ) {
        requireFeature();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createSaving(ownerId(auth), req));
    }

    @PatchMapping("/savings/{id}")
    public FinanceModels.SavingView updateSaving(
            Authentication auth, @PathVariable("id") String id, @RequestBody FinanceModels.SavingWrite req
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
    public List<FinanceModels.LoanView> listLoans(Authentication auth) {
        requireFeature();
        return service.listLoans(ownerId(auth));
    }

    @PostMapping("/loans")
    public ResponseEntity<FinanceModels.LoanView> createLoan(
            Authentication auth, @RequestBody FinanceModels.LoanWrite req
    ) {
        requireFeature();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createLoan(ownerId(auth), req));
    }

    @PatchMapping("/loans/{id}")
    public FinanceModels.LoanView updateLoan(
            Authentication auth, @PathVariable("id") String id, @RequestBody FinanceModels.LoanWrite req
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
    public List<FinanceModels.FixedView> listFixed(Authentication auth) {
        requireFeature();
        return service.listFixed(ownerId(auth));
    }

    @PostMapping("/fixed")
    public ResponseEntity<FinanceModels.FixedView> createFixed(
            Authentication auth, @RequestBody FinanceModels.FixedWrite req
    ) {
        requireFeature();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createFixed(ownerId(auth), req));
    }

    @PatchMapping("/fixed/{id}")
    public FinanceModels.FixedView updateFixed(
            Authentication auth, @PathVariable("id") String id, @RequestBody FinanceModels.FixedWrite req
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
    public FinanceModels.SettingsView getSettings(Authentication auth) {
        requireFeature();
        return service.getSettings(ownerId(auth));
    }

    @PutMapping("/settings")
    public FinanceModels.SettingsView updateSettings(
            Authentication auth, @RequestBody FinanceModels.SettingsWrite req
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
