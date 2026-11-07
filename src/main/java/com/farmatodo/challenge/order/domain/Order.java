package com.farmatodo.challenge.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Order aggregate. Holds a <em>snapshot</em> of what was bought (product name and unit price at purchase time),
 * so later catalog changes never alter past orders.
 * <p>
 * State machine: {@code PENDING_PAYMENT → PAID} or {@code PENDING_PAYMENT → PAYMENT_FAILED}. Final states are
 * immutable: any other transition is a programming error and fails fast.
 */
public final class Order {

    private final UUID id;
    private final UUID customerId;
    private final UUID cardId;
    private final String deliveryAddress;
    private final List<OrderItem> items;
    private final List<PaymentAttempt> attempts;
    private final BigDecimal total;
    private final UUID transactionId;
    private final Instant createdAt;
    private OrderStatus status;
    private Instant updatedAt;

    public Order(UUID id, UUID customerId, UUID cardId, String deliveryAddress, List<OrderItem> items,
            List<PaymentAttempt> attempts, OrderStatus status, UUID transactionId, Instant createdAt,
            Instant updatedAt) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one item");
        }
        this.id = id;
        this.customerId = Objects.requireNonNull(customerId);
        this.cardId = Objects.requireNonNull(cardId);
        this.deliveryAddress = Objects.requireNonNull(deliveryAddress);
        this.items = List.copyOf(items);
        this.attempts = new ArrayList<>(attempts);
        this.total = items.stream().map(OrderItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        this.status = Objects.requireNonNull(status);
        this.transactionId = transactionId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** A new order waiting for payment. */
    public static Order place(UUID customerId, UUID cardId, String deliveryAddress, List<OrderItem> items,
            UUID transactionId, Instant now) {
        return new Order(null, customerId, cardId, deliveryAddress, items, List.of(), OrderStatus.PENDING_PAYMENT,
                transactionId, now, now);
    }

    public PaymentAttempt recordAttempt(PaymentResult result, Instant at) {
        requirePending();
        PaymentAttempt attempt = new PaymentAttempt(attempts.size() + 1, result, at);
        attempts.add(attempt);
        updatedAt = at;
        return attempt;
    }

    public void markPaid(Instant at) {
        requirePending();
        if (attempts.isEmpty() || attempts.getLast().result() != PaymentResult.APPROVED) {
            throw new IllegalStateException("Cannot mark as paid without an approved payment attempt");
        }
        status = OrderStatus.PAID;
        updatedAt = at;
    }

    public void markPaymentFailed(Instant at) {
        requirePending();
        status = OrderStatus.PAYMENT_FAILED;
        updatedAt = at;
    }

    public Order withId(UUID newId) {
        return new Order(newId, customerId, cardId, deliveryAddress, items, attempts, status, transactionId,
                createdAt, updatedAt);
    }

    public boolean belongsTo(UUID customer) {
        return customerId.equals(customer);
    }

    private void requirePending() {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Order " + id + " is already " + status);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID customerId() {
        return customerId;
    }

    public UUID cardId() {
        return cardId;
    }

    public String deliveryAddress() {
        return deliveryAddress;
    }

    public List<OrderItem> items() {
        return items;
    }

    public List<PaymentAttempt> attempts() {
        return Collections.unmodifiableList(attempts);
    }

    public BigDecimal total() {
        return total;
    }

    public OrderStatus status() {
        return status;
    }

    public UUID transactionId() {
        return transactionId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public enum OrderStatus { PENDING_PAYMENT, PAID, PAYMENT_FAILED }

    public enum PaymentResult { APPROVED, REJECTED }

    /** Snapshot of a purchased product: name and price are copied, not referenced. */
    public record OrderItem(UUID productId, String productName, BigDecimal unitPrice, int quantity) {
        public OrderItem {
            if (quantity < 1) {
                throw new IllegalArgumentException("quantity must be positive");
            }
        }

        public BigDecimal subtotal() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    public record PaymentAttempt(int attemptNumber, PaymentResult result, Instant at) {
    }
}
