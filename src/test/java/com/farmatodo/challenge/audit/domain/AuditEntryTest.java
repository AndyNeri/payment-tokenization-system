package com.farmatodo.challenge.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.farmatodo.challenge.audit.domain.AuditEntry.Outcome;

import org.junit.jupiter.api.Test;

class AuditEntryTest {

    @Test
    void requiresTheTraceabilityFieldsAndDefaultsDetails() {
        AuditEntry entry = AuditEntry.of(UUID.randomUUID(), "X", null, null, Outcome.SUCCESS, null, Instant.now());
        assertThat(entry.details()).isEmpty();
        assertThat(entry.entityId()).isNull();

        assertThatThrownBy(() -> AuditEntry.of(null, "X", null, null, Outcome.SUCCESS, null, Instant.now()))
                .isInstanceOf(NullPointerException.class).hasMessage("transactionId");
    }
}
