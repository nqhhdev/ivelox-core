package com.ivelox.core.modules.paymentapproval.domain.model;

import java.time.YearMonth;

public record PaymentSummary(YearMonth month, long totalAmountMinor, long paymentCount) {
}
