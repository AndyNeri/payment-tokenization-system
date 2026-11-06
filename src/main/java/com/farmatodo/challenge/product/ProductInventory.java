package com.farmatodo.challenge.product;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the product module for other modules: cart reads product data, order reserves/releases stock.
 * The product module is the only owner of stock.
 */
public interface ProductInventory {

    Optional<ProductSnapshot> find(UUID productId);

    Map<UUID, ProductSnapshot> findAll(Collection<UUID> productIds);

    /**
     * Atomically decrements stock for every product, all-or-nothing within the caller's transaction.
     *
     * @throws InsufficientStockException if any product lacks stock (the caller's transaction must roll back)
     */
    void reserve(Map<UUID, Integer> quantities);

    /** Gives stock back (e.g. after a failed payment). */
    void release(Map<UUID, Integer> quantities);

    record ProductSnapshot(UUID id, String sku, String name, BigDecimal price, int stock) {
    }
}
