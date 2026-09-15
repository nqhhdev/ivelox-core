package com.ivelox.core.modules.order.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ivelox.core.modules.order.domain.model.Order;

public record OrderResponse(
        UUID id,
        String customerName,
        BigDecimal totalAmount,
        String status,
        Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(), order.customerName(), order.totalAmount(),
                order.status().name(), order.createdAt());
    }
}
