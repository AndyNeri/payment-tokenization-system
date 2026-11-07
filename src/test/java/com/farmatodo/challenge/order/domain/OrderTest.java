package com.farmatodo.challenge.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.farmatodo.challenge.order.domain.Order.OrderItem;
import com.farmatodo.challenge.order.domain.Order.OrderStatus;
import com.farmatodo.challenge.order.domain.Order.PaymentResult;

import org.junit.jupiter.api.Test;

class OrderTest {

    static final Instant T0 = Instant.parse("2026-11-07T10:00:00Z");
    final UUID customer = UUID.randomUUID();

    Order newOrder() {
        return Order.place(customer, UUID.randomUUID(), "Av. Principal 123", List.of(
                new OrderItem(UUID.randomUUID(), "Ibuprofeno", new BigDecimal("4.20"), 3),
                new OrderItem(UUID.randomUUID(), "Vitamina C", new BigDecimal("8.40"), 1)), UUID.randomUUID(), T0);
    }

    @Test
    void totalIsTheExactSumOfSnapshots() {
        Order order = newOrder();
        assertThat(order.total()).isEqualByComparingTo("21.00");
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.belongsTo(customer)).isTrue();
        assertThat(order.items().get(0).subtotal()).isEqualByComparingTo("12.60");
    }

    @Test
    void attemptsAreNumberedAndPaidRequiresAnApprovedLastAttempt() {
        Order order = newOrder();
        order.recordAttempt(PaymentResult.REJECTED, T0.plusSeconds(1));
        assertThatThrownBy(() -> order.markPaid(T0)).isInstanceOf(IllegalStateException.class);

        order.recordAttempt(PaymentResult.APPROVED, T0.plusSeconds(2));
        order.markPaid(T0.plusSeconds(3));

        assertThat(order.attempts()).extracting(Order.PaymentAttempt::attemptNumber).containsExactly(1, 2);
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.updatedAt()).isEqualTo(T0.plusSeconds(3));
    }

    @Test
    void finalStatesAreImmutable() {
        Order failed = newOrder();
        failed.markPaymentFailed(T0);

        assertThatThrownBy(() -> failed.recordAttempt(PaymentResult.APPROVED, T0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> failed.markPaymentFailed(T0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> failed.attempts().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void invalidOrdersAreRejected() {
        assertThatThrownBy(() -> Order.place(customer, UUID.randomUUID(), "addr", List.of(), null, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderItem(UUID.randomUUID(), "x", BigDecimal.ONE, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withIdKeepsEverythingElse() {
        Order order = newOrder();
        UUID id = UUID.randomUUID();
        Order copy = order.withId(id);
        assertThat(copy.id()).isEqualTo(id);
        assertThat(copy.total()).isEqualByComparingTo(order.total());
        assertThat(copy.cardId()).isEqualTo(order.cardId());
        assertThat(copy.createdAt()).isEqualTo(T0);
    }
}
