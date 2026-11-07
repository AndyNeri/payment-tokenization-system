package com.farmatodo.challenge.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after an email is handed to the SMTP server (consumed by audit). No email address or content:
 * the audit trail must not become a copy of personal data.
 */
public record NotificationSent(String type, UUID orderId, UUID customerId, UUID transactionId, Instant occurredAt) {
}
