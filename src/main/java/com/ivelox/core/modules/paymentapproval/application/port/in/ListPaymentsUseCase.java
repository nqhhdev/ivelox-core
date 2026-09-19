package com.ivelox.core.modules.paymentapproval.application.port.in;

import java.util.List;
import com.ivelox.core.modules.paymentapproval.domain.model.Payment;

public interface ListPaymentsUseCase {
    List<Payment> list(String userId);
}
