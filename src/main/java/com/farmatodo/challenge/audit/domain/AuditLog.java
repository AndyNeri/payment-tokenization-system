package com.farmatodo.challenge.audit.domain;

import java.util.List;
import java.util.UUID;

/** Outbound port: append-only storage of the audit trail. */
public interface AuditLog {

    void append(AuditEntry entry);

    /** Every entry of one transaction, oldest first. */
    List<AuditEntry> findByTransactionId(UUID transactionId);
}
