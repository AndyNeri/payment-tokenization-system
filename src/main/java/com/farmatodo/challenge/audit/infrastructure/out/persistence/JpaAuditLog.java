package com.farmatodo.challenge.audit.infrastructure.out.persistence;

import java.util.List;
import java.util.UUID;

import com.farmatodo.challenge.audit.domain.AuditEntry;
import com.farmatodo.challenge.audit.domain.AuditLog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

/** Outbound adapter: the audit trail in the {@code audit_events} table (details as JSONB). */
@Component
class JpaAuditLog implements AuditLog {

    private final AuditEventJpaRepository repository;

    JpaAuditLog(AuditEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(AuditEntry e) {
        repository.save(new AuditEventJpaEntity(e.transactionId(), e.eventType(), e.entityType(), e.entityId(),
                e.outcome(), e.details(), e.occurredAt()));
    }

    @Override
    public List<AuditEntry> findByTransactionId(UUID transactionId) {
        return repository.findByTransactionIdOrderByCreatedAtAscIdAsc(transactionId).stream()
                .map(e -> new AuditEntry(e.getId(), e.getTransactionId(), e.getEventType(), e.getEntityType(),
                        e.getEntityId(), e.getOutcome(), e.getDetails(), e.getCreatedAt()))
                .toList();
    }
}

interface AuditEventJpaRepository extends JpaRepository<AuditEventJpaEntity, UUID> {
    List<AuditEventJpaEntity> findByTransactionIdOrderByCreatedAtAscIdAsc(UUID transactionId);
}
