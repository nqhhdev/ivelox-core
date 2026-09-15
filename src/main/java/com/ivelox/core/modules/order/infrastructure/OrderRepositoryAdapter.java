package com.ivelox.core.modules.order.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ivelox.core.modules.order.application.port.out.OrderRepositoryPort;
import com.ivelox.core.modules.order.domain.model.Order;

/** Adapter: implements the domain-facing port using JPA, and maps Entity <-> domain model. */
@Component
class OrderRepositoryAdapter implements OrderRepositoryPort {

    private final SpringDataOrderRepository jpaRepository;

    OrderRepositoryAdapter(SpringDataOrderRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Order save(Order order) {
        OrderEntity entity = new OrderEntity(
                order.id(), order.customerName(), order.totalAmount(), order.status(), order.createdAt());
        jpaRepository.save(entity);
        return order;
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    private Order toDomain(OrderEntity entity) {
        return Order.reconstitute(
                entity.getId(), entity.getCustomerName(), entity.getTotalAmount(),
                entity.getStatus(), entity.getCreatedAt());
    }
}
