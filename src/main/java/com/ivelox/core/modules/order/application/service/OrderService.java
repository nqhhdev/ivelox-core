package com.ivelox.core.modules.order.application.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ivelox.core.modules.order.application.port.in.CreateOrderUseCase;
import com.ivelox.core.modules.order.application.port.in.GetOrderUseCase;
import com.ivelox.core.modules.order.application.port.out.OrderRepositoryPort;
import com.ivelox.core.modules.order.domain.model.Order;

@Service
@Transactional
public class OrderService implements CreateOrderUseCase, GetOrderUseCase {

    private final OrderRepositoryPort orderRepository;

    public OrderService(OrderRepositoryPort orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public Order createOrder(String customerName, BigDecimal totalAmount) {
        Order order = Order.create(customerName, totalAmount);
        return orderRepository.save(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrder(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
    }
}
