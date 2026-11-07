package com.farmatodo.challenge.audit.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One line of the audit trail: what happened, to which entity, with what outcome, inside which transaction.
 * {@code details} holds non-sensitive extra data (never PAN, CVV, emails or passwords).
 */
public record AuditEntry(UUID id, UUID transactionId, String eventType, String entityType, String entityId,
                         Outcome outcome, Map<String, Object> details, Instant occurredAt) {

    public enum Outcome { SUCCESS, FAILURE }

    public AuditEntry {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(occurredAt, "occurredAt");
        details = details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));  // nulls allowed
    }

    public static AuditEntry of(UUID transactionId, String eventType, String entityType, Object entityId,
            Outcome outcome, Map<String, Object> details, Instant occurredAt) {
        return new AuditEntry(null, transactionId, eventType, entityType,
                entityId == null ? null : entityId.toString(), outcome, details, occurredAt);
    }
}
