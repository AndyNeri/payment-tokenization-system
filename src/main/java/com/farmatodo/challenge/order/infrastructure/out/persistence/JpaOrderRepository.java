package com.farmatodo.challenge.order.infrastructure.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.farmatodo.challenge.order.domain.Order;
import com.farmatodo.challenge.order.domain.Order.OrderItem;
import com.farmatodo.challenge.order.domain.Order.PaymentAttempt;
import com.farmatodo.challenge.order.domain.OrderPorts.OrderRepository;
import com.farmatodo.challenge.order.infrastructure.out.persistence.OrderJpaEntity.OrderItemJpaEntity;
import com.farmatodo.challenge.order.infrastructure.out.persistence.OrderJpaEntity.PaymentAttemptJpaEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

/**
 * Maps the order aggregate. Items are written once (they are an immutable snapshot); later saves only append
 * new payment attempts and update status/timestamps.
 */
@Component
class JpaOrderRepository implements OrderRepository {

    private final OrderJpaRepository repository;

    JpaOrderRepository(OrderJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Order save(Order order) {
        OrderJpaEntity entity = order.id() == null ? newEntity(order) : repository.findById(order.id())
                .orElseThrow(() -> new IllegalStateException("Order " + order.id() + " not found"));

        for (PaymentAttempt attempt : order.attempts()) {
            boolean known = entity.attempts.stream().anyMatch(a -> a.attemptNumber == attempt.attemptNumber());
            if (!known) {
                PaymentAttemptJpaEntity row = new PaymentAttemptJpaEntity();
                row.order = entity;
                row.attemptNumber = attempt.attemptNumber();
                row.result = attempt.result();
                row.createdAt = attempt.at();
                entity.attempts.add(row);
            }
        }
        entity.status = order.status();
        entity.updatedAt = order.updatedAt();
        return toDomain(repository.saveAndFlush(entity));
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return repository.findById(id).map(JpaOrderRepository::toDomain);
    }

    @Override
    public List<Order> findByCustomer(UUID customerId) {
        return repository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(JpaOrderRepository::toDomain)
                .toList();
    }

    private static OrderJpaEntity newEntity(Order order) {
        OrderJpaEntity entity = new OrderJpaEntity();
        entity.customerId = order.customerId();
        entity.cardId = order.cardId();
        entity.deliveryAddress = order.deliveryAddress();
        entity.total = order.total();
        entity.transactionId = order.transactionId();
        entity.createdAt = order.createdAt();
        for (OrderItem item : order.items()) {
            OrderItemJpaEntity row = new OrderItemJpaEntity();
            row.order = entity;
            row.productId = item.productId();
            row.productName = item.productName();
            row.unitPrice = item.unitPrice();
            row.quantity = item.quantity();
            entity.items.add(row);
        }
        return entity;
    }

    private static Order toDomain(OrderJpaEntity e) {
        List<OrderItem> items = e.items.stream()
                .map(i -> new OrderItem(i.productId, i.productName, i.unitPrice, i.quantity))
                .toList();
        List<PaymentAttempt> attempts = e.attempts.stream()
                .map(a -> new PaymentAttempt(a.attemptNumber, a.result, a.createdAt))
                .toList();
        return new Order(e.id, e.customerId, e.cardId, e.deliveryAddress, items, attempts, e.status,
                e.transactionId, e.createdAt, e.updatedAt);
    }
}

interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID> {

    List<OrderJpaEntity> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}
