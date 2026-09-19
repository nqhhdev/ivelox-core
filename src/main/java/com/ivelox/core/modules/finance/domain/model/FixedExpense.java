package com.ivelox.core.modules.finance.domain.model;

import java.time.Instant;
import java.util.UUID;

public record FixedExpense(
        UUID id,
        String userId,
        String name,
        long amountMinor,
        String currency,
        int dayOfMonth,
        Instant createdAt
) {
}
