package com.ivelox.core.modules.paymentapproval.application.port.in;

import java.util.UUID;
import com.ivelox.core.modules.paymentapproval.domain.model.Payment;

public interface RejectPaymentUseCase {
    Payment reject(String userId, UUID id, String otp);
}
