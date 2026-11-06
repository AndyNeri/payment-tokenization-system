package com.farmatodo.challenge.product;

import java.time.Instant;
import java.util.UUID;

/**
 * Published on every product search. Its listener stores the search asynchronously (challenge requirement),
 * outside the request's critical path.
 *
 * @param query      null for "list all"
 * @param customerId who searched (nullable for future anonymous searches)
 */
public record ProductsSearched(String query, long resultsCount, UUID customerId, UUID transactionId,
                               Instant occurredAt) {
}
