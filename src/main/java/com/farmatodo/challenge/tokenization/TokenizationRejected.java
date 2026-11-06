package com.farmatodo.challenge.tokenization;

import java.time.Instant;
import java.util.UUID;

/** Published when a tokenization is rejected by the simulated rejection policy (consumed by audit). */
public record TokenizationRejected(UUID customerId, String brand, String last4, String reason, UUID transactionId,
                                   Instant occurredAt) {
}
