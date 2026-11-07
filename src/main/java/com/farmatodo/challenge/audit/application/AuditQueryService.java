package com.farmatodo.challenge.audit.application;

import java.util.List;
import java.util.UUID;

import com.farmatodo.challenge.audit.domain.AuditEntry;
import com.farmatodo.challenge.audit.domain.AuditLog;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the audit trail: everything that happened inside one transactionId. */
@Service
public class AuditQueryService {

    private final AuditLog auditLog;

    AuditQueryService(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> byTransaction(UUID transactionId) {
        return auditLog.findByTransactionId(transactionId);
    }
}
