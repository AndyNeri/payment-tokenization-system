package com.farmatodo.challenge.shared.tracing;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.MDC;

/**
 * Access point for the current request's transaction id (stored in the SLF4J MDC by {@link TransactionIdFilter}).
 * Every log line, audit event and error response carries this id.
 */
public final class TransactionContext {

    public static final String HEADER = "X-Transaction-Id";
    public static final String MDC_KEY = "transactionId";

    private TransactionContext() {
    }

    public static Optional<UUID> current() {
        String value = MDC.get(MDC_KEY);
        return value == null ? Optional.empty() : Optional.of(UUID.fromString(value));
    }

    /** Current transaction id, or a new one when running outside a request (e.g. scheduled jobs). */
    public static UUID currentOrNew() {
        return current().orElseGet(UUID::randomUUID);
    }

    static void set(UUID transactionId) {
        MDC.put(MDC_KEY, transactionId.toString());
    }

    static void clear() {
        MDC.remove(MDC_KEY);
    }
}
