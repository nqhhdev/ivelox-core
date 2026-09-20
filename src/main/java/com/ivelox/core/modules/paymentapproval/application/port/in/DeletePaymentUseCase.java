package com.ivelox.core.modules.paymentapproval.application.port.in;

import java.util.UUID;

public interface DeletePaymentUseCase {
    void deleteOne(String userId, UUID id);
}
