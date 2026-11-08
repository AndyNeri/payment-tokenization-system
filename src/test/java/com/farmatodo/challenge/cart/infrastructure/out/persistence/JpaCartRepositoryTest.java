package com.farmatodo.challenge.cart.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.farmatodo.challenge.cart.domain.Cart;
import com.farmatodo.challenge.cart.domain.Cart.CartItem;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** The cart adapter updates rows in place instead of delete + insert (keeps UNIQUE(cart_id, product_id) happy). */
class JpaCartRepositoryTest {

    static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");

    CartJpaRepository springData = mock(CartJpaRepository.class);
    JpaCartRepository adapter = new JpaCartRepository(springData, Clock.fixed(NOW, ZoneOffset.UTC));
    UUID customer = UUID.randomUUID();
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    UUID c = UUID.randomUUID();

    @Test
    void firstSaveCreatesTheCartWithItsItems() {
        when(springData.findByCustomerId(customer)).thenReturn(Optional.empty());
        when(springData.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        Cart cart = Cart.empty(customer);
        cart.add(a, 2);

        Cart saved = adapter.save(cart);

        assertThat(saved.customerId()).isEqualTo(customer);
        assertThat(saved.items()).containsExactly(new CartItem(a, 2));
    }

    @Test
    void laterSavesUpdateKeepAddAndRemoveRowsInPlace() {
        CartJpaEntity entity = new CartJpaEntity(customer);
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        CartJpaEntity.CartItemJpaEntity rowA = new CartJpaEntity.CartItemJpaEntity(entity, a, 1);
        CartJpaEntity.CartItemJpaEntity rowB = new CartJpaEntity.CartItemJpaEntity(entity, b, 1);
        entity.getItems().add(rowA);
        entity.getItems().add(rowB);
        when(springData.findByCustomerId(customer)).thenReturn(Optional.of(entity));
        when(springData.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        Cart cart = adapter.findByCustomer(customer).orElseThrow();
        cart.add(a, 2);          // a: 1 → 3 (same row)
        cart.remove(b);          // b: removed
        cart.add(c, 4);          // c: new row
        Cart saved = adapter.save(cart);

        assertThat(entity.getItems()).contains(rowA).doesNotContain(rowB).hasSize(2);
        assertThat(rowA.getQuantity()).isEqualTo(3);
        assertThat(saved.items()).containsExactlyInAnyOrder(new CartItem(a, 3), new CartItem(c, 4));
        assertThat(ReflectionTestUtils.getField(entity, "updatedAt")).isEqualTo(NOW);
    }
}
