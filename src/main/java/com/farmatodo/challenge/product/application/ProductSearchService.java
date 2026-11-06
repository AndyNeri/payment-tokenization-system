package com.farmatodo.challenge.product.application;

import java.time.Clock;
import java.util.UUID;

import com.farmatodo.challenge.product.ProductsSearched;
import com.farmatodo.challenge.product.domain.Product;
import com.farmatodo.challenge.product.domain.ProductPorts.PageResult;
import com.farmatodo.challenge.product.domain.ProductPorts.ProductRepository;
import com.farmatodo.challenge.shared.tracing.TransactionContext;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product search. The search itself is synchronous; storing it is not: an event is published and a listener
 * persists it after commit, on another thread. The user never waits for (nor is affected by) the log write.
 */
@Service
public class ProductSearchService {

    private final ProductRepository products;
    private final ProductProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ProductSearchService(ProductRepository products, ProductProperties properties, ApplicationEventPublisher events,
            Clock clock) {
        this.products = products;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Not read-only on purpose: the event is written to the outbox table (event_publication) in this same
     * transaction, which guarantees it is not lost even if the app stops before the listener runs.
     */
    @Transactional
    public PageResult<Product> search(String query, int page, int size, UUID customerId) {
        String normalized = query == null || query.isBlank() ? null : query.strip();
        PageResult<Product> result = products.search(normalized, properties.minStock(), page, size);
        events.publishEvent(new ProductsSearched(normalized, result.totalElements(), customerId,
                TransactionContext.currentOrNew(), clock.instant()));
        return result;
    }
}
