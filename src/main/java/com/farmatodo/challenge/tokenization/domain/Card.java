package com.farmatodo.challenge.tokenization.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A tokenized card as stored in the vault. Only {@code brand} and {@code last4} are in clear text;
 * the PAN, expiration date and holder name live inside {@link EncryptedCard}.
 *
 * @param id null until persisted (UUID v7 assigned by the persistence adapter)
 */
public record Card(UUID id, String token, UUID customerId, CardBrand brand, String last4,
                   EncryptedCard encrypted, Instant createdAt) {

    public Card withId(UUID newId) {
        return new Card(newId, token, customerId, brand, last4, encrypted, createdAt);
    }

    public boolean belongsTo(UUID customer) {
        return customerId.equals(customer);
    }
}
