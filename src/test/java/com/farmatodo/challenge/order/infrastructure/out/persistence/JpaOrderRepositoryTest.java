package com.farmatodo.challenge.order.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.farmatodo.challenge.order.domain.Order;
import com.farmatodo.challenge.order.domain.Order.OrderItem;
import com.farmatodo.challenge.order.domain.Order.OrderStatus;
import com.farmatodo.challenge.order.domain.Order.PaymentResult;

import org.junit.jupiter.api.Test;

/** The order adapter without a database: Spring Data is mocked and the mapping is checked field by field. */
class JpaOrderRepositoryTest {

    static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");

    OrderJpaRepository springData = mock(OrderJpaRepository.class);
    JpaOrderRepository adapter = new JpaOrderRepository(springData);

    UUID customer = UUID.randomUUID();
    UUID card = UUID.randomUUID();
    UUID tx = UUID.randomUUID();

    Order newOrder() {
        return Order.place(customer, card, "Calle 1", List.of(
                new OrderItem(UUID.randomUUID(), "Ibuprofeno", new BigDecimal("4.20"), 2),
                new OrderItem(UUID.randomUUID(), "Vitamina C", new BigDecimal("8.40"), 1)), tx, NOW);
    }

    /** Simulates the database: assigns the id that Hibernate would generate. */
    void saveAssignsIds() {
        when(springData.saveAndFlush(any())).thenAnswer(inv -> {
            OrderJpaEntity e = inv.getArgument(0);
            if (e.id == null) {
                e.id = UUID.randomUUID();
            }
            return e;
        });
    }

    @Test
    void newOrderIsWrittenWithItsSnapshotAndMappedBack() {
        saveAssignsIds();

        Order saved = adapter.save(newOrder());

        assertThat(saved.id()).isNotNull();
        assertThat(saved.customerId()).isEqualTo(customer);
        assertThat(saved.cardId()).isEqualTo(card);
        assertThat(saved.deliveryAddress()).isEqualTo("Calle 1");
        assertThat(saved.transactionId()).isEqualTo(tx);
        assertThat(saved.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(saved.total()).isEqualByComparingTo("16.80");
        assertThat(saved.items()).extracting(OrderItem::productName).containsExactly("Ibuprofeno", "Vitamina C");
        assertThat(saved.attempts()).isEmpty();
    }

    @Test
    void laterSavesOnlyAppendNewAttemptsAndUpdateTheStatus() {
        saveAssignsIds();
        Order order = adapter.save(newOrder());
        OrderJpaEntity stored = new OrderJpaEntity();
        stored.id = order.id();
        stored.customerId = customer;
        stored.cardId = card;
        stored.deliveryAddress = "Calle 1";
        stored.total = order.total();
        stored.transactionId = tx;
        stored.createdAt = NOW;
        order.items().forEach(i -> {
            OrderJpaEntity.OrderItemJpaEntity row = new OrderJpaEntity.OrderItemJpaEntity();
            row.order = stored;
            row.productId = i.productId();
            row.productName = i.productName();
            row.unitPrice = i.unitPrice();
            row.quantity = i.quantity();
            stored.items.add(row);
        });
        when(springData.findById(order.id())).thenReturn(Optional.of(stored));

        order.recordAttempt(PaymentResult.REJECTED, NOW.plusSeconds(1));
        adapter.save(order);                                   // attempt 1 appended
        order.recordAttempt(PaymentResult.APPROVED, NOW.plusSeconds(2));
        order.markPaid(NOW.plusSeconds(3));
        Order paid = adapter.save(order);                      // only attempt 2 appended

        assertThat(stored.attempts).extracting(a -> a.attemptNumber).containsExactly(1, 2);
        assertThat(paid.status()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.attempts()).extracting(Order.PaymentAttempt::result)
                .containsExactly(PaymentResult.REJECTED, PaymentResult.APPROVED);
        assertThat(stored.updatedAt).isEqualTo(NOW.plusSeconds(3));
    }

    @Test
    void savingAnOrderThatDisappearedFails() {
        UUID id = UUID.randomUUID();
        when(springData.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.save(newOrder().withId(id)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(id.toString());
    }

    @Test
    void findsByIdAndByCustomer() {
        saveAssignsIds();
        Order saved = adapter.save(newOrder());
        OrderJpaEntity entity = new OrderJpaEntity();
        entity.id = saved.id();
        entity.customerId = customer;
        entity.cardId = card;
        entity.deliveryAddress = "Calle 1";
        entity.status = OrderStatus.PAYMENT_FAILED;
        entity.total = new BigDecimal("4.20");
        entity.createdAt = NOW;
        entity.updatedAt = NOW;
        OrderJpaEntity.OrderItemJpaEntity item = new OrderJpaEntity.OrderItemJpaEntity();
        item.productId = UUID.randomUUID();
        item.productName = "Ibuprofeno";
        item.unitPrice = new BigDecimal("4.20");
        item.quantity = 1;
        entity.items.add(item);
        when(springData.findById(entity.id)).thenReturn(Optional.of(entity));
        when(springData.findByCustomerIdOrderByCreatedAtDesc(customer)).thenReturn(List.of(entity));

        assertThat(adapter.findById(entity.id)).get().extracting(Order::status).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
        assertThat(adapter.findByCustomer(customer)).singleElement().extracting(Order::id).isEqualTo(entity.id);
    }
}
