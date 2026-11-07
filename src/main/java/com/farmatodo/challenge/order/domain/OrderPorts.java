package com.farmatodo.challenge.order.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.farmatodo.challenge.order.domain.Order.PaymentResult;

/** Outbound ports of the order slice. */
public final class OrderPorts {

    private OrderPorts() {
    }

    public interface OrderRepository {
        /** Inserts a new order or updates status/attempts of an existing one. */
        Order save(Order order);

        Optional<Order> findById(UUID id);

        List<Order> findByCustomer(UUID customerId);
    }

    /**
     * Payment processor. Today a simulation with a configurable approval probability; tomorrow a real PSP
     * (Stripe, Adyen...) would be another adapter, without touching the checkout use case.
     * It receives the card <em>token</em>, never card data: the PAN stays in the vault.
     */
    public interface PaymentGateway {
        PaymentResult charge(PaymentRequest request);
    }

    public record PaymentRequest(UUID orderId, String cardToken, BigDecimal amount, int attempt) {
    }

    /** Waits between retries. A port so tests do not sleep. */
    public interface Backoff {
        void pause(Duration duration);
    }
}
