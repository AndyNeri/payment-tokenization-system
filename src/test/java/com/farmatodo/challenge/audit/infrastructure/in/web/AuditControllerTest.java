package com.farmatodo.challenge.audit.infrastructure.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.farmatodo.challenge.StandaloneMvc;
import com.farmatodo.challenge.audit.application.AuditQueryService;
import com.farmatodo.challenge.audit.domain.AuditEntry;
import com.farmatodo.challenge.audit.domain.AuditEntry.Outcome;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

class AuditControllerTest {

    AuditQueryService audit = mock(AuditQueryService.class);
    MockMvcTester mvc = StandaloneMvc.of(new AuditController(audit));

    @Test
    void returnsTheTrailOfTheTransaction() {
        UUID tx = UUID.randomUUID();
        when(audit.byTransaction(tx)).thenReturn(List.of(new AuditEntry(UUID.randomUUID(), tx, "ORDER_PLACED", "ORDER",
                "o-1", Outcome.SUCCESS, Map.of("total", 21.0), Instant.parse("2026-11-08T10:00:00Z"))));

        assertThat(mvc.get().uri("/api/v1/audit").param("transactionId", tx.toString()))
                .hasStatusOk().bodyJson().extractingPath("$[0].outcome").isEqualTo("SUCCESS");
    }

    @Test
    void missingOrInvalidTransactionIdIs400() {
        assertThat(mvc.get().uri("/api/v1/audit")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/api/v1/audit").param("transactionId", "x")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("BAD_REQUEST");
    }
}
