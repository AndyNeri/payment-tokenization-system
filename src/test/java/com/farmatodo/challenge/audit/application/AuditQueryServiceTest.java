package com.farmatodo.challenge.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.farmatodo.challenge.audit.domain.AuditEntry;
import com.farmatodo.challenge.audit.domain.AuditEntry.Outcome;
import com.farmatodo.challenge.audit.domain.AuditLog;

import org.junit.jupiter.api.Test;

class AuditQueryServiceTest {

    @Test
    void returnsOnlyTheRequestedTransaction() {
        List<AuditEntry> rows = new ArrayList<>();
        AuditLog log = new AuditLog() {
            @Override
            public void append(AuditEntry entry) {
                rows.add(entry);
            }

            @Override
            public List<AuditEntry> findByTransactionId(UUID transactionId) {
                return rows.stream().filter(r -> r.transactionId().equals(transactionId)).toList();
            }
        };
        UUID mine = UUID.randomUUID();
        log.append(AuditEntry.of(mine, "ORDER_PLACED", "ORDER", "o-1", Outcome.SUCCESS, Map.of(), Instant.now()));
        log.append(AuditEntry.of(UUID.randomUUID(), "ORDER_PLACED", "ORDER", "o-2", Outcome.SUCCESS, Map.of(), Instant.now()));

        assertThat(new AuditQueryService(log).byTransaction(mine)).extracting(AuditEntry::entityId).containsExactly("o-1");
    }
}
