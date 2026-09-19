package com.ivelox.core.modules.paymentapproval.application.port.in;

import java.time.YearMonth;
import com.ivelox.core.modules.paymentapproval.domain.model.PaymentSummary;

public interface GetPaymentSummaryUseCase {
    PaymentSummary summary(String userId, YearMonth month);
}
