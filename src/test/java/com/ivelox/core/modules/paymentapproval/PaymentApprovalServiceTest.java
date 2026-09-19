package com.ivelox.core.modules.paymentapproval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.ivelox.core.modules.paymentapproval.application.port.out.PaymentApprovalRepositoryPort;
import com.ivelox.core.modules.paymentapproval.application.service.PaymentApprovalService;
import com.ivelox.core.modules.paymentapproval.domain.model.Payment;
import com.ivelox.core.modules.paymentapproval.domain.model.PaymentStatus;
import com.ivelox.core.modules.paymentapproval.domain.model.PaymentSummary;

class PaymentApprovalServiceTest {

    @Mock
    private PaymentApprovalRepositoryPort repo;
    private PaymentApprovalService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new PaymentApprovalService(repo);
    }

    @Test
    void createReturnsPendingPayment() {
        when(repo.insert(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = service.create("owner");

        assertEquals("owner", payment.userId());
        assertEquals(PaymentStatus.PENDING, payment.status());
        assertEquals("AED", payment.currency());
        org.junit.jupiter.api.Assertions.assertTrue(payment.amountMinor() > 0);
    }

    @Test
    void onlyPendingPaymentCanBeDecided() {
        Payment pending = payment(PaymentStatus.PENDING);
        when(repo.decide(eq("owner"), eq(pending.id()), eq(PaymentStatus.APPROVED), any(Instant.class))).thenReturn(1);
        when(repo.find("owner", pending.id())).thenReturn(Optional.of(pending.approve(Instant.now())));

        assertEquals(PaymentStatus.APPROVED, service.approve("owner", pending.id(), "8888").status());

        Payment finalPayment = payment(PaymentStatus.APPROVED);
        when(repo.decide(eq("owner"), eq(finalPayment.id()), eq(PaymentStatus.REJECTED), any(Instant.class))).thenReturn(0);
        when(repo.find("owner", finalPayment.id())).thenReturn(Optional.of(finalPayment));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.reject("owner", finalPayment.id(), "8888"));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals("payment_already_decided", error.getReason());
    }

    @Test
    void unknownPaymentReturnsNotFoundAndWrongOtpIsRejected() {
        UUID id = UUID.randomUUID();
        when(repo.find("owner", id)).thenReturn(Optional.empty());

        ResponseStatusException missing = assertThrows(ResponseStatusException.class,
                () -> service.approve("owner", id, "8888"));
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        assertEquals("payment_not_found", missing.getReason());

        ResponseStatusException otp = assertThrows(ResponseStatusException.class,
                () -> service.reject("owner", id, "0000"));
        assertEquals(HttpStatus.BAD_REQUEST, otp.getStatusCode());
        assertEquals("invalid_otp", otp.getReason());
    }

    @Test
    void summaryDelegatesApprovedMonth() {
        when(repo.approvedSummary(any(), any(Instant.class), any(Instant.class)))
                .thenReturn(new PaymentSummary(YearMonth.of(2026, 9), 154000, 2));

        PaymentSummary result = service.summary("owner", YearMonth.of(2026, 9));

        assertEquals(154000, result.totalAmountMinor());
        assertEquals(2, result.paymentCount());
    }

    @Test
    void listUsesDecidedRepositoryQuery() {
        Payment approved = payment(PaymentStatus.APPROVED);
        when(repo.listDecided("owner")).thenReturn(List.of(approved));

        assertEquals(List.of(approved), service.list("owner"));
    }

    @Test
    void pendingPaymentCannotBeOpenedAsDetails() {
        Payment pending = payment(PaymentStatus.PENDING);
        when(repo.find("owner", pending.id())).thenReturn(Optional.of(pending));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.get("owner", pending.id()));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals("payment_not_decided", error.getReason());
    }

    private static Payment payment(PaymentStatus status) {
        return new Payment(UUID.randomUUID(), "owner", "Ahmed K.", 1250, "AED", status,
                "PAY-TEST", "note", Instant.parse("2026-09-10T10:30:00Z"),
                status == PaymentStatus.PENDING ? null : Instant.parse("2026-09-10T10:32:00Z"));
    }
}
