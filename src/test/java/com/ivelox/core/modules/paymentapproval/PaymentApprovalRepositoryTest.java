package com.ivelox.core.modules.paymentapproval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.ivelox.core.modules.paymentapproval.domain.model.Payment;
import com.ivelox.core.modules.paymentapproval.domain.model.PaymentStatus;
import com.ivelox.core.modules.paymentapproval.infrastructure.PaymentApprovalRepositoryAdapter;

@SpringBootTest
@ActiveProfiles("test")
class PaymentApprovalRepositoryTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PaymentApprovalRepositoryAdapter repository;

    @BeforeEach
    void clean() {
        jdbc.update("delete from payment_approval_payments");
    }

    @Test
    void decidedListOrdersByDecisionTimeAndPendingListCanReload() {
        Payment createdLaterDecidedEarlier = payment("PAY-1", Instant.parse("2026-09-10T10:00:00Z"));
        Payment createdEarlierDecidedLater = payment("PAY-2", Instant.parse("2026-09-09T10:00:00Z"));
        repository.insert(createdLaterDecidedEarlier);
        repository.insert(createdEarlierDecidedLater);
        repository.decide("owner", createdLaterDecidedEarlier.id(), PaymentStatus.APPROVED,
                Instant.parse("2026-09-10T10:01:00Z"));
        repository.decide("owner", createdEarlierDecidedLater.id(), PaymentStatus.REJECTED,
                Instant.parse("2026-09-11T10:01:00Z"));

        var decided = repository.listDecided("owner");

        assertEquals(createdEarlierDecidedLater.id(), decided.get(0).id());
        assertEquals(0, repository.listPending("owner").size());
    }

    @Test
    void summaryUsesDecisionWindow() {
        Payment createdInSeptemberDecidedInOctober = payment("PAY-3", Instant.parse("2026-09-30T23:00:00Z"));
        Payment createdInAugustDecidedInSeptember = payment("PAY-4", Instant.parse("2026-08-31T23:00:00Z"));
        repository.insert(createdInSeptemberDecidedInOctober);
        repository.insert(createdInAugustDecidedInSeptember);
        repository.decide("owner", createdInSeptemberDecidedInOctober.id(), PaymentStatus.APPROVED,
                Instant.parse("2026-10-01T01:00:00Z"));
        repository.decide("owner", createdInAugustDecidedInSeptember.id(), PaymentStatus.APPROVED,
                Instant.parse("2026-09-01T01:00:00Z"));

        var summary = repository.approvedSummary("owner",
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z"));

        assertEquals(1250, summary.totalAmountMinor());
        assertEquals(1, summary.paymentCount());
    }

    @Test
    void deleteByIdsRemovesMatchingRows() {
        Payment a = payment("PAY-DEL-1", Instant.parse("2026-09-10T10:00:00Z"));
        Payment b = payment("PAY-DEL-2", Instant.parse("2026-09-10T11:00:00Z"));
        repository.insert(a);
        repository.insert(b);

        assertEquals(2, repository.deleteByIds("owner", java.util.List.of(a.id(), b.id())));
        assertEquals(0, repository.listPending("owner").size());
        assertEquals(0, repository.deleteByIds("owner", java.util.List.of(a.id())));
    }

    private static Payment payment(String reference, Instant createdAt) {
        return new Payment(UUID.randomUUID(), "owner", "Ahmed K.", 1250, "AED", PaymentStatus.PENDING,
                reference, "note", createdAt, null);
    }
}
