package com.ivelox.core.modules.order.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Pure domain model — no Spring, no JPA. Business rules for an order live here. */
public final class Order {

    private final UUID id;
    private final String customerName;
    private final BigDecimal totalAmount;
    private final OrderStatus status;
    private final Instant createdAt;

    private Order(UUID id, String customerName, BigDecimal totalAmount, OrderStatus status, Instant createdAt) {
        this.id = id;
        this.customerName = customerName;
        this.totalAmount = totalAmount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Order create(String customerName, BigDecimal totalAmount) {
        if (customerName == null || customerName.isBlank()) {
            throw new IllegalArgumentException("customerName is required");
        }
        if (totalAmount == null || totalAmount.signum() <= 0) {
            throw new IllegalArgumentException("totalAmount must be positive");
        }
        return new Order(UUID.randomUUID(), customerName, totalAmount, OrderStatus.CREATED, Instant.now());
    }

    public static Order reconstitute(UUID id, String customerName, BigDecimal totalAmount,
                                      OrderStatus status, Instant createdAt) {
        return new Order(id, customerName, totalAmount, status, createdAt);
    }

    public UUID id() {
        return id;
    }

    public String customerName() {
        return customerName;
    }

    public BigDecimal totalAmount() {
        return totalAmount;
    }

    public OrderStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
