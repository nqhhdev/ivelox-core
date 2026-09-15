package com.ivelox.core.modules.order.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.ivelox.core.modules.order.domain.model.Order;

public interface OrderRepositoryPort {

    Order save(Order order);

    Optional<Order> findById(UUID id);
}
