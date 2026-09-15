package com.ivelox.core.modules.order.application.port.in;

import java.util.UUID;

import com.ivelox.core.modules.order.domain.model.Order;

public interface GetOrderUseCase {

    Order getOrder(UUID id);
}
