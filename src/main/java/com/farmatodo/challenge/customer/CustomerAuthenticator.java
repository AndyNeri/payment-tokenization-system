package com.farmatodo.challenge.customer;

import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the customer module for the auth module: checks credentials without exposing password hashes.
 * The customer module owns the credentials; auth only receives "who is it" or "nobody".
 */
public interface CustomerAuthenticator {

    /** @return the customer id when the email exists and the password matches; empty otherwise. */
    Optional<UUID> authenticate(String email, String rawPassword);
}
