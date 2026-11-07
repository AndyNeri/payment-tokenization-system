package com.farmatodo.challenge.tokenization;

import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the tokenization module for other modules (order): resolves tokens to masked card data.
 * Never exposes the PAN; the vault stays the only place where encrypted card data lives.
 */
public interface CardLookup {

    /** The card behind {@code token}, only if it belongs to {@code customerId} (otherwise empty: no info leak). */
    Optional<CardSummary> findOwned(String token, UUID customerId);

    Optional<CardSummary> findById(UUID cardId);

    record CardSummary(UUID cardId, String token, String brand, String last4) {
    }
}
