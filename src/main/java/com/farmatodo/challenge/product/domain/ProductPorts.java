package com.farmatodo.challenge.product.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound ports of the product slice. */
public final class ProductPorts {

    private ProductPorts() {
    }

    public interface ProductRepository {

        /** Products with {@code stock > minStockExclusive}, optionally matching {@code query}, ordered by name. */
        PageResult<Product> search(String query, int minStockExclusive, int page, int size);

        Optional<Product> findById(UUID id);

        List<Product> findAllById(Collection<UUID> ids);

        /** Atomic {@code stock = stock - quantity} only if enough stock; returns false otherwise. */
        boolean decrementStock(UUID id, int quantity);

        void incrementStock(UUID id, int quantity);
    }

    public interface SearchLogRepository {
        void save(SearchLog log);
    }

    public record SearchLog(String query, long resultsCount, UUID customerId, UUID transactionId, Instant createdAt) {
    }

    public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }
}
