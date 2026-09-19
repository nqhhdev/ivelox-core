package com.ivelox.core.modules.finance.domain.model;

import java.time.LocalDate;

public record Upcoming(String kind, String name, LocalDate dueOn, long amountMinor, String currency) {
}
