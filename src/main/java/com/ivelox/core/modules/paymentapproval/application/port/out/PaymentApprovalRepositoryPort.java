package com.ivelox.core.modules.paymentapproval.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.ivelox.core.modules.paymentapproval.domain.model.Payment;
import com.ivelox.core.modules.paymentapproval.domain.model.PaymentStatus;
import com.ivelox.core.modules.paymentapproval.domain.model.PaymentSummary;

public interface PaymentApprovalRepositoryPort {
    Payment insert(Payment payment);
    List<Payment> listDecided(String userId);
    List<Payment> listPending(String userId);
    Optional<Payment> find(String userId, UUID id);
    PaymentSummary approvedSummary(String userId, Instant from, Instant to);
    int decide(String userId, UUID id, PaymentStatus status, Instant decidedAt);
    int deleteByIds(String userId, List<UUID> ids);
}
