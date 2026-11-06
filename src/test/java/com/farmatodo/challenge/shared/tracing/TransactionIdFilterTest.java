package com.farmatodo.challenge.shared.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TransactionIdFilterTest {

    final TransactionIdFilter filter = new TransactionIdFilter();

    @Test
    void reusesValidClientId() {
        UUID id = UUID.randomUUID();
        assertThat(TransactionIdFilter.resolve(" " + id + " ")).isEqualTo(id);
    }

    @Test
    void generatesIdWhenHeaderIsMissingBlankOrMalformed() {
        assertThat(TransactionIdFilter.resolve(null)).isNotNull();
        assertThat(TransactionIdFilter.resolve("  ")).isNotNull();
        assertThat(TransactionIdFilter.resolve("not-a-uuid")).isNotNull();
    }

    @Test
    void exposesIdInMdcDuringRequestAndClearsItAfterwards() throws Exception {
        UUID id = UUID.randomUUID();
        var request = new MockHttpServletRequest();
        request.addHeader(TransactionContext.HEADER, id.toString());
        var response = new MockHttpServletResponse();
        AtomicReference<UUID> seenInsideChain = new AtomicReference<>();

        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seenInsideChain.set(TransactionContext.current().orElseThrow());
            }
        });

        assertThat(seenInsideChain.get()).isEqualTo(id);
        assertThat(response.getHeader(TransactionContext.HEADER)).isEqualTo(id.toString());
        assertThat(TransactionContext.current()).isEmpty();
    }

    @Test
    void currentOrNewGeneratesIdOutsideRequests() {
        assertThat(TransactionContext.current()).isEmpty();
        assertThat(TransactionContext.currentOrNew()).isNotNull();
    }
}
