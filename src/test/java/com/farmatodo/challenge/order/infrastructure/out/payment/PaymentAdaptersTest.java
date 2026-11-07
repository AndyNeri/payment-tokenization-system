package com.farmatodo.challenge.order.infrastructure.out.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import com.farmatodo.challenge.order.application.PaymentProperties;
import com.farmatodo.challenge.order.domain.Order.PaymentResult;
import com.farmatodo.challenge.order.domain.OrderPorts.PaymentRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PaymentAdaptersTest {

    static final PaymentRequest REQUEST = new PaymentRequest(UUID.randomUUID(), "tok_x", BigDecimal.TEN, 1);

    @ParameterizedTest(name = "p={0}, r={1} -> {2}")
    @CsvSource({"0.3, 0.29, REJECTED", "0.3, 0.3, APPROVED", "0.0, 0.0, APPROVED", "1.0, 0.99, REJECTED"})
    void rejectsWhenRandomIsBelowProbability(double p, double r, PaymentResult expected) {
        var gateway = new SimulatedPaymentGateway(new PaymentProperties(p, 3, Duration.ZERO), () -> r);
        assertThat(gateway.charge(REQUEST)).isEqualTo(expected);
    }

    @Test
    void defaultConstructorUsesRealRandomness() {
        var alwaysApproves = new SimulatedPaymentGateway(new PaymentProperties(0.0, 3, Duration.ZERO));
        assertThat(alwaysApproves.charge(REQUEST)).isEqualTo(PaymentResult.APPROVED);
    }

    @Test
    void backoffSleepsAndIgnoresZero() {
        var backoff = new ThreadSleepBackoff();
        long start = System.nanoTime();
        backoff.pause(Duration.ZERO);
        backoff.pause(Duration.ofMillis(-5));
        backoff.pause(Duration.ofMillis(30));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(30));
    }

    @Test
    void backoffRestoresInterruptFlag() {
        var backoff = new ThreadSleepBackoff();
        Thread.currentThread().interrupt();
        assertThatThrownBy(() -> backoff.pause(Duration.ofSeconds(1))).isInstanceOf(IllegalStateException.class);
        assertThat(Thread.interrupted()).isTrue();   // flag preserved (and cleared here for the next tests)
    }
}
