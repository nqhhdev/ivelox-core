package com.ivelox.core.modules.paymentapproval.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Payment(
        UUID id,
        String userId,
        String recipientName,
        long amountMinor,
        String currency,
        PaymentStatus status,
        String reference,
        String note,
        Instant createdAt,
        Instant decidedAt
) {
    public static Payment createIncoming(String userId, String recipientName, long amountMinor,
                                         String reference, String note, Instant createdAt) {
        if (userId == null || userId.isBlank() || recipientName == null || recipientName.isBlank()
                || amountMinor < 1 || reference == null || reference.isBlank() || createdAt == null) {
            throw new IllegalArgumentException("invalid payment");
        }
        return new Payment(UUID.randomUUID(), userId, recipientName, amountMinor, "AED",
                PaymentStatus.PENDING, reference, note == null ? "" : note, createdAt, null);
    }

    public Payment approve(Instant now) {
        requirePending();
        return new Payment(id, userId, recipientName, amountMinor, currency, PaymentStatus.APPROVED,
                reference, note, createdAt, now);
    }

    public Payment reject(Instant now) {
        requirePending();
        return new Payment(id, userId, recipientName, amountMinor, currency, PaymentStatus.REJECTED,
                reference, note, createdAt, now);
    }

    public boolean isDecided() {
        return status != PaymentStatus.PENDING;
    }

    private void requirePending() {
        if (isDecided()) {
            throw new IllegalStateException("payment_already_decided");
        }
    }
}
