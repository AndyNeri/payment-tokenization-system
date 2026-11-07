package com.farmatodo.challenge.order.infrastructure.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import com.farmatodo.challenge.order.domain.Order.OrderStatus;
import com.farmatodo.challenge.order.domain.Order.PaymentResult;

import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "orders")
class OrderJpaEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    UUID customerId;

    @Column(name = "card_id", nullable = false, updatable = false)
    UUID cardId;

    @Column(name = "delivery_address", nullable = false, updatable = false)
    String deliveryAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    OrderStatus status;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    BigDecimal total;

    @Column(name = "transaction_id", updatable = false)
    UUID transactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("productName")
    List<OrderItemJpaEntity> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("attemptNumber")
    List<PaymentAttemptJpaEntity> attempts = new ArrayList<>();

    protected OrderJpaEntity() {
        // for JPA
    }

    @Entity
    @Table(name = "order_items")
    static class OrderItemJpaEntity {

        @Id
        @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
        UUID id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "order_id", nullable = false)
        OrderJpaEntity order;

        @Column(name = "product_id", nullable = false)
        UUID productId;

        @Column(name = "product_name", nullable = false, length = 150)
        String productName;

        @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
        BigDecimal unitPrice;

        @Column(nullable = false)
        int quantity;

        protected OrderItemJpaEntity() {
            // for JPA
        }
    }

    @Entity
    @Table(name = "payment_attempts")
    static class PaymentAttemptJpaEntity {

        @Id
        @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
        UUID id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "order_id", nullable = false)
        OrderJpaEntity order;

        @Column(name = "attempt_number", nullable = false)
        int attemptNumber;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        PaymentResult result;

        @Column(name = "created_at", nullable = false)
        Instant createdAt;

        protected PaymentAttemptJpaEntity() {
            // for JPA
        }
    }
}
