package com.farmatodo.challenge.customer;

import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the customer module for other modules (tokenization checks the card owner exists; order uses the
 * default delivery address; notification needs the email). Other modules never touch customer tables.
 */
public interface CustomerDirectory {

    boolean exists(UUID customerId);

    Optional<CustomerContact> contactOf(UUID customerId);

    /** The minimum contact data other modules need. */
    record CustomerContact(UUID id, String firstName, String lastName, String email, String address) {
    }
}
