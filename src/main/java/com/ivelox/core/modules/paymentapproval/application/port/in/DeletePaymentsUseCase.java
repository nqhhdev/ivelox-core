package com.ivelox.core.modules.paymentapproval.application.port.in;

import java.util.List;
import java.util.UUID;

public interface DeletePaymentsUseCase {
    int deleteMany(String userId, List<UUID> ids);
}
