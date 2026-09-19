package com.ivelox.core.modules.finance.domain.model;

public record Settings(
        String userId,
        String homeCurrency,
        int digestHour,
        boolean digestEnabled,
        boolean overSlotAlert,
        boolean overMonthAlert
) {
}
