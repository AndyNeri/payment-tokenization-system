package com.farmatodo.challenge.cart.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.farmatodo.challenge.shared.error.Exceptions.InvalidInputException;
import com.farmatodo.challenge.shared.error.Exceptions.NotFoundException;

import org.junit.jupiter.api.Test;

class CartTest {

    final UUID customer = UUID.randomUUID();
    final UUID product = UUID.randomUUID();

    @Test
    void addingTheSameProductMergesQuantities() {
        Cart cart = Cart.empty(customer);

        assertThat(cart.add(product, 2)).isEqualTo(2);
        assertThat(cart.add(product, 3)).isEqualTo(5);
        assertThat(cart.items()).containsExactly(new Cart.CartItem(product, 5));
        assertThat(cart.customerId()).isEqualTo(customer);
        assertThat(cart.id()).isNull();
    }

    @Test
    void quantityLimits() {
        Cart cart = Cart.empty(customer);
        cart.add(product, 98);

        assertThatThrownBy(() -> cart.add(product, 2)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> cart.add(product, 0)).isInstanceOf(InvalidInputException.class);
        assertThat(cart.quantityOf(product)).isEqualTo(98);
    }

    @Test
    void removeAndClear() {
        Cart cart = Cart.empty(customer);
        cart.add(product, 1);
        cart.add(UUID.randomUUID(), 1);

        cart.remove(product);
        assertThat(cart.find(product)).isEmpty();
        assertThatThrownBy(() -> cart.remove(product))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "CART_ITEM_NOT_FOUND");

        cart.clear();
        assertThat(cart.isEmpty()).isTrue();
    }

    @Test
    void itemsAreReadOnlyFromOutside() {
        Cart cart = Cart.empty(customer);
        cart.add(product, 1);
        assertThatThrownBy(() -> cart.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
