package com.farmatodo.challenge.tokenization;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a card is tokenized. Part of the module's public API (consumed by audit).
 * Carries no sensitive data: only brand and last 4 digits.
 */
public record CardTokenized(UUID cardId, UUID customerId, String brand, String last4, UUID transactionId,
                            Instant occurredAt) {
}
