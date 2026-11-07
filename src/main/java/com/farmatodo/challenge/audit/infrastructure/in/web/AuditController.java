package com.farmatodo.challenge.audit.infrastructure.in.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.farmatodo.challenge.audit.application.AuditQueryService;
import com.farmatodo.challenge.audit.domain.AuditEntry;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Audit trail query, protected by API key (operations/support use, not customers). */
@RestController
@RequestMapping("/api/v1/audit")
class AuditController {

    private final AuditQueryService audit;

    AuditController(AuditQueryService audit) {
        this.audit = audit;
    }

    @GetMapping
    /** Everything that happened inside one transactionId, oldest first. */
    List<AuditEventResponse> byTransaction(@RequestParam UUID transactionId) {
        return audit.byTransaction(transactionId).stream().map(AuditEventResponse::from).toList();
    }

    record AuditEventResponse(UUID id, UUID transactionId, String eventType, String entityType, String entityId,
                              String outcome, Map<String, Object> details, Instant occurredAt) {
        static AuditEventResponse from(AuditEntry e) {
            return new AuditEventResponse(e.id(), e.transactionId(), e.eventType(), e.entityType(), e.entityId(),
                    e.outcome().name(), e.details(), e.occurredAt());
        }
    }
}
