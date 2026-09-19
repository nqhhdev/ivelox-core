package com.ivelox.core.modules.paymentapproval.api;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.ivelox.core.config.IveloxProperties;
import com.ivelox.core.modules.paymentapproval.api.PaymentApprovalDtos.DecisionRequest;
import com.ivelox.core.modules.paymentapproval.api.PaymentApprovalDtos.CreateRequest;
import com.ivelox.core.modules.paymentapproval.api.PaymentApprovalDtos.PaymentList;
import com.ivelox.core.modules.paymentapproval.api.PaymentApprovalDtos.PaymentSummaryView;
import com.ivelox.core.modules.paymentapproval.api.PaymentApprovalDtos.PaymentView;
import com.ivelox.core.modules.paymentapproval.application.port.in.ApprovePaymentUseCase;
import com.ivelox.core.modules.paymentapproval.application.port.in.CreatePaymentRequestUseCase;
import com.ivelox.core.modules.paymentapproval.application.port.in.GetPaymentDetailsUseCase;
import com.ivelox.core.modules.paymentapproval.application.port.in.GetPaymentSummaryUseCase;
import com.ivelox.core.modules.paymentapproval.application.port.in.ListPaymentsUseCase;
import com.ivelox.core.modules.paymentapproval.application.port.in.ListPendingPaymentsUseCase;
import com.ivelox.core.modules.paymentapproval.application.port.in.RejectPaymentUseCase;

@RestController
@RequestMapping("/api/v1/payment-approval")
@Tag(name = "Payment Approval", description = "Demo AED payment approval workflow (no auth)")
public class PaymentApprovalController {

    /** Single-owner demo tenant — routes are public. */
    private static final String DEMO_USER = "owner";

    private final IveloxProperties props;
    private final CreatePaymentRequestUseCase create;
    private final ListPaymentsUseCase list;
    private final ListPendingPaymentsUseCase pending;
    private final GetPaymentDetailsUseCase details;
    private final GetPaymentSummaryUseCase summary;
    private final ApprovePaymentUseCase approve;
    private final RejectPaymentUseCase reject;

    public PaymentApprovalController(IveloxProperties props, CreatePaymentRequestUseCase create,
                                     ListPaymentsUseCase list, ListPendingPaymentsUseCase pending,
                                     GetPaymentDetailsUseCase details,
                                     GetPaymentSummaryUseCase summary, ApprovePaymentUseCase approve,
                                     RejectPaymentUseCase reject) {
        this.props = props;
        this.create = create;
        this.list = list;
        this.pending = pending;
        this.details = details;
        this.summary = summary;
        this.approve = approve;
        this.reject = reject;
    }

    @GetMapping("/payments")
    @Operation(summary = "List decided payments", description = "Returns approved and rejected payments, newest first.")
    public PaymentList payments() {
        requireFeature();
        return new PaymentList(list.list(DEMO_USER).stream().map(PaymentView::of).toList());
    }

    @GetMapping("/requests")
    @Operation(summary = "List pending payment requests", description = "Reloads pending requests after the client restarts.")
    public PaymentList pending() {
        requireFeature();
        return new PaymentList(pending.listPending(DEMO_USER).stream().map(PaymentView::of).toList());
    }

    @GetMapping("/summary")
    @Operation(summary = "Get monthly approved payment summary")
    public PaymentSummaryView summary(@RequestParam String month) {
        requireFeature();
        return PaymentSummaryView.of(summary.summary(DEMO_USER, parseMonth(month)));
    }

    @GetMapping("/payments/{id}")
    @Operation(summary = "Get a decided payment")
    public PaymentView details(@PathVariable String id) {
        requireFeature();
        return PaymentView.of(details.get(DEMO_USER, parseUuid(id)));
    }

    @PostMapping("/requests")
    @Operation(summary = "Create a random pending incoming payment request")
    public ResponseEntity<PaymentView> create(@RequestBody(required = false) CreateRequest ignored) {
        requireFeature();
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentView.of(create.create(DEMO_USER)));
    }

    @PutMapping("/requests/{id}/approve")
    @Operation(summary = "Approve a pending payment request", description = "Demo OTP is 8888.")
    public PaymentView approve(@PathVariable String id, @RequestBody DecisionRequest request) {
        requireFeature();
        return PaymentView.of(approve.approve(DEMO_USER, parseUuid(id), request == null ? null : request.otp()));
    }

    @PutMapping("/requests/{id}/reject")
    @Operation(summary = "Reject a pending payment request", description = "Demo OTP is 8888.")
    public PaymentView reject(@PathVariable String id, @RequestBody DecisionRequest request) {
        requireFeature();
        return PaymentView.of(reject.reject(DEMO_USER, parseUuid(id), request == null ? null : request.otp()));
    }

    private void requireFeature() {
        if (!props.paymentApprovalEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "payment_feature_disabled");
        }
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_id");
        }
    }

    private static YearMonth parseMonth(String raw) {
        try {
            return YearMonth.parse(raw);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_month");
        }
    }
}
