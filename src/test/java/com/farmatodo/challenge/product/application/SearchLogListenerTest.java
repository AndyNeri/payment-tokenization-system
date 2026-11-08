package com.farmatodo.challenge.product.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.farmatodo.challenge.product.ProductsSearched;
import com.farmatodo.challenge.product.domain.ProductPorts.SearchLog;

import org.junit.jupiter.api.Test;

class SearchLogListenerTest {

    @Test
    void storesTheSearchWithItsTransactionId() {
        List<SearchLog> stored = new ArrayList<>();
        UUID customer = UUID.randomUUID();
        UUID tx = UUID.randomUUID();
        Instant at = Instant.parse("2026-11-08T10:00:00Z");

        new SearchLogListener(stored::add).on(new ProductsSearched("ibuprofeno", 2, customer, tx, at));

        assertThat(stored).containsExactly(new SearchLog("ibuprofeno", 2, customer, tx, at));
    }
}
