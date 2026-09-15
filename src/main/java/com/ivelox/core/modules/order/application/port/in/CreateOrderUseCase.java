package com.ivelox.core.modules.order.application.port.in;

import java.math.BigDecimal;

import com.ivelox.core.modules.order.domain.model.Order;

public interface CreateOrderUseCase {

    Order createOrder(String customerName, BigDecimal totalAmount);
}
