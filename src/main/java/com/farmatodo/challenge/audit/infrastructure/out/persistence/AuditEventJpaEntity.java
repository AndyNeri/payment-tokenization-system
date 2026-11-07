package com.farmatodo.challenge.audit.infrastructure.out.persistence;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.farmatodo.challenge.audit.domain.AuditEntry.Outcome;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_events")
class AuditEventJpaEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "entity_type", length = 30)
    private String entityType;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Outcome outcome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditEventJpaEntity() {
        // for JPA
    }

    AuditEventJpaEntity(UUID transactionId, String eventType, String entityType, String entityId, Outcome outcome,
            Map<String, Object> details, Instant createdAt) {
        this.transactionId = transactionId;
        this.eventType = eventType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.outcome = outcome;
        this.details = details;
        this.createdAt = createdAt;
    }

    UUID getId() { return id; }
    UUID getTransactionId() { return transactionId; }
    String getEventType() { return eventType; }
    String getEntityType() { return entityType; }
    String getEntityId() { return entityId; }
    Outcome getOutcome() { return outcome; }
    Map<String, Object> getDetails() { return details; }
    Instant getCreatedAt() { return createdAt; }
}
