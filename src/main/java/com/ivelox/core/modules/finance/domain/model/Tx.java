package com.ivelox.core.modules.finance.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record Tx(
        UUID id,
        String userId,
        String kind,
        long amountMinor,
        String currency,
        String category,
        String note,
        LocalDate occurredOn,
        UUID incomeId,
        UUID savingGoalId,
        UUID loanId,
        UUID fixedExpenseId,
        Instant createdAt
) {
}
