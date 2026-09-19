package com.ivelox.core.modules.finance.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Loan(
        UUID id,
        String userId,
        String name,
        long principalMinor,
        long monthlyPaymentMinor,
        String currency,
        int dayOfMonth,
        String note,
        Instant createdAt
) {
}
