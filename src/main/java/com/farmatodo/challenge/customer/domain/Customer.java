package com.farmatodo.challenge.customer.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A registered customer. {@code email} and {@code phone} are always stored normalised
 * (see {@link ContactNormalizer}) so uniqueness checks cannot be bypassed with formatting tricks.
 *
 * @param id null until persisted (UUID v7 assigned by the persistence adapter)
 */
public record Customer(UUID id, String firstName, String lastName, String email, String phone, String address,
                       String passwordHash, Instant createdAt) {

    @Override
    public String toString() {
        return "Customer[id=" + id + ", email=" + email + "]";   // never print the password hash
    }
}
