package com.farmatodo.challenge.product.application;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.farmatodo.challenge.product.InsufficientStockException;
import com.farmatodo.challenge.product.ProductInventory;
import com.farmatodo.challenge.product.domain.Product;
import com.farmatodo.challenge.product.domain.ProductPorts.ProductRepository;
import com.farmatodo.challenge.shared.error.Exceptions.NotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Implementation of the product module API (stock is owned here). */
@Service
class ProductInventoryService implements ProductInventory {

    private final ProductRepository products;

    ProductInventoryService(ProductRepository products) {
        this.products = products;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductSnapshot> find(UUID productId) {
        return products.findById(productId).map(ProductInventoryService::snapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ProductSnapshot> findAll(Collection<UUID> productIds) {
        return products.findAllById(productIds).stream()
                .map(ProductInventoryService::snapshot)
                .collect(Collectors.toMap(ProductSnapshot::id, Function.identity()));
    }

    /**
     * Each line is an atomic conditional UPDATE ({@code ... WHERE stock >= :q}): the database decides, so stock can
     * never go negative even under heavy concurrency, and no retries are needed. Rows are updated in a fixed
     * order (sorted by id) so two orders with the same products cannot deadlock each other.
     * Runs inside the caller's transaction (MANDATORY): a failure rolls back every previous decrement.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(Map<UUID, Integer> quantities) {
        new TreeMap<>(quantities).forEach((productId, quantity) -> {
            if (!products.decrementStock(productId, quantity)) {
                Product product = products.findById(productId)
                        .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product " + productId + " does not exist"));
                throw new InsufficientStockException(productId, product.name(), product.stock());
            }
        });
    }

    @Override
    @Transactional
    public void release(Map<UUID, Integer> quantities) {
        new TreeMap<>(quantities).forEach(products::incrementStock);
    }

    private static ProductSnapshot snapshot(Product p) {
        return new ProductSnapshot(p.id(), p.sku(), p.name(), p.price(), p.stock());
    }
}
