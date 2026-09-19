package com.ivelox.core.modules.finance.domain.model;

import java.time.Instant;
import java.util.UUID;

public record SavingGoal(
        UUID id,
        String userId,
        String name,
        long targetAmountMinor,
        long monthlyAmountMinor,
        String currency,
        int dayOfMonth,
        Instant createdAt
) {
}
