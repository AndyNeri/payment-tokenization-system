package com.farmatodo.challenge.order.infrastructure.out.payment;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

import com.farmatodo.challenge.order.application.PaymentProperties;
import com.farmatodo.challenge.order.domain.Order.PaymentResult;
import com.farmatodo.challenge.order.domain.OrderPorts.PaymentGateway;
import com.farmatodo.challenge.order.domain.OrderPorts.PaymentRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Simulated payment processor (challenge requirement): each attempt is rejected with the configured probability
 * ({@code r < p}, same semantics as tokenization, ADR-007). A real PSP integration would replace this adapter.
 */
@Component
class SimulatedPaymentGateway implements PaymentGateway {

    private final PaymentProperties properties;
    private final DoubleSupplier random;

    @Autowired
    SimulatedPaymentGateway(PaymentProperties properties) {
        this(properties, () -> ThreadLocalRandom.current().nextDouble());
    }

    SimulatedPaymentGateway(PaymentProperties properties, DoubleSupplier random) {
        this.properties = properties;
        this.random = random;
    }

    @Override
    public PaymentResult charge(PaymentRequest request) {
        return random.getAsDouble() < properties.rejectionProbability() ? PaymentResult.REJECTED : PaymentResult.APPROVED;
    }
}
