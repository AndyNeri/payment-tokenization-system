package com.farmatodo.challenge.cart;

import java.util.List;
import java.util.UUID;

/** Public API of the cart module, used by the order module at checkout. */
public interface ShoppingCart {

    /** Current lines of the customer's cart (empty list if there is no cart). */
    List<CartLine> linesOf(UUID customerId);

    /** Empties the cart after a successful order. */
    void clear(UUID customerId);

    record CartLine(UUID productId, int quantity) {
    }
}
