package com.ivelox.core.modules.paymentapproval.application.port.in;

import java.util.UUID;
import com.ivelox.core.modules.paymentapproval.domain.model.Payment;

public interface GetPaymentDetailsUseCase {
    Payment get(String userId, UUID id);
}
