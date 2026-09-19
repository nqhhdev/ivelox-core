package com.ivelox.core.modules.finance.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Income(
        UUID id,
        String userId,
        String name,
        long amountMinor,
        String currency,
        int dayOfMonth,
        String recurrence,
        Instant createdAt
) {
}
