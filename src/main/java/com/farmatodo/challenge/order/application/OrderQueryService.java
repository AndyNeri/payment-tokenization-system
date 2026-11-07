package com.farmatodo.challenge.order.application;

import java.util.List;
import java.util.UUID;

import com.farmatodo.challenge.order.domain.Order;
import com.farmatodo.challenge.order.domain.OrderPorts.OrderRepository;
import com.farmatodo.challenge.shared.error.Exceptions.NotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderQueryService {

    private final OrderRepository orders;

    OrderQueryService(OrderRepository orders) {
        this.orders = orders;
    }

    /** Someone else's order is reported as 404, not 403: its existence is not revealed. */
    @Transactional(readOnly = true)
    public Order getOwned(UUID orderId, UUID customerId) {
        return orders.findById(orderId)
                .filter(order -> order.belongsTo(customerId))
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order " + orderId + " not found"));
    }

    @Transactional(readOnly = true)
    public List<Order> ordersOf(UUID customerId) {
        return orders.findByCustomer(customerId);
    }
}
