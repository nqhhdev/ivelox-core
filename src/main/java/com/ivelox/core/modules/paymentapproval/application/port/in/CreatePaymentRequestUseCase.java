package com.ivelox.core.modules.paymentapproval.application.port.in;

import com.ivelox.core.modules.paymentapproval.domain.model.Payment;

public interface CreatePaymentRequestUseCase {
    Payment create(String userId);
}
