package com.farmatodo.challenge.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain events of the order module (public API). Notification sends emails on PaymentSucceeded/PaymentFailed;
 * audit records every one of them. None carries card data.
 */
public final class OrderEvents {

    private OrderEvents() {
    }

    public record OrderPlaced(UUID orderId, UUID customerId, BigDecimal total, int items, UUID transactionId,
                              Instant occurredAt) {
    }

    public record PaymentAttempted(UUID orderId, int attempt, String result, UUID transactionId, Instant occurredAt) {
    }

    public record PaymentSucceeded(UUID orderId, UUID customerId, BigDecimal total, int attempts, UUID transactionId,
                                   Instant occurredAt) {
    }

    public record PaymentFailed(UUID orderId, UUID customerId, BigDecimal total, int attempts, UUID transactionId,
                                Instant occurredAt) {
    }
}
