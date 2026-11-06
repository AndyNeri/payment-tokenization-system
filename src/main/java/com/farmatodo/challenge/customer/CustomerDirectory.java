package com.farmatodo.challenge.customer;

import java.util.UUID;

/**
 * Public API of the customer module for other modules (e.g. tokenization checks the card owner exists).
 * Other modules depend on this interface, never on customer tables or repositories.
 */
public interface CustomerDirectory {

    boolean exists(UUID customerId);
}
