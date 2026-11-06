package com.farmatodo.challenge.customer;

import java.time.Instant;
import java.util.UUID;

/** Published when a customer registers (consumed by audit). No personal data beyond the id. */
public record CustomerRegistered(UUID customerId, UUID transactionId, Instant occurredAt) {
}
