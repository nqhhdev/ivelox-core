package com.ivelox.core.modules.order.infrastructure;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataOrderRepository extends JpaRepository<OrderEntity, UUID> {
}
