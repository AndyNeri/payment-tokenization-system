package com.farmatodo.challenge.audit.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.farmatodo.challenge.audit.domain.AuditEntry;
import com.farmatodo.challenge.audit.domain.AuditEntry.Outcome;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class JpaAuditLogTest {

    AuditEventJpaRepository springData = mock(AuditEventJpaRepository.class);
    JpaAuditLog log = new JpaAuditLog(springData);
    UUID tx = UUID.randomUUID();
    Instant now = Instant.parse("2026-11-08T10:00:00Z");

    @Test
    void appendStoresEveryFieldIncludingJsonDetails() {
        log.append(AuditEntry.of(tx, "ORDER_PLACED", "ORDER", UUID.randomUUID(), Outcome.SUCCESS,
                Map.of("total", 21.0), now));

        ArgumentCaptor<AuditEventJpaEntity> saved = ArgumentCaptor.forClass(AuditEventJpaEntity.class);
        verify(springData).save(saved.capture());
        assertThat(saved.getValue().getTransactionId()).isEqualTo(tx);
        assertThat(saved.getValue().getEventType()).isEqualTo("ORDER_PLACED");
        assertThat(saved.getValue().getOutcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(saved.getValue().getDetails()).containsEntry("total", 21.0);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(now);
    }

    @Test
    void readsTheTrailOfOneTransaction() {
        AuditEventJpaEntity row = new AuditEventJpaEntity(tx, "EMAIL_SENT", "ORDER", "o-1", Outcome.SUCCESS,
                Map.of("type", "PAYMENT_SUCCEEDED_EMAIL"), now);
        ReflectionTestUtils.setField(row, "id", UUID.randomUUID());
        when(springData.findByTransactionIdOrderByCreatedAtAscIdAsc(tx)).thenReturn(List.of(row));

        List<AuditEntry> trail = log.findByTransactionId(tx);

        assertThat(trail).singleElement().satisfies(e -> {
            assertThat(e.id()).isEqualTo(row.getId());
            assertThat(e.entityType()).isEqualTo("ORDER");
            assertThat(e.entityId()).isEqualTo("o-1");
            assertThat(e.details()).containsEntry("type", "PAYMENT_SUCCEEDED_EMAIL");
        });
    }
}
