package com.farmatodo.challenge.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.farmatodo.challenge.IntegrationTest;
import com.farmatodo.challenge.TestData;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class StockReservationIntegrationTest {

    @Autowired
    ProductInventory inventory;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void concurrentReservationsNeverOversell() throws Exception {
        UUID product = TestData.insertProduct(jdbc, "Último lote", new BigDecimal("10.00"), 5);
        int buyers = 20;
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            tasks.add(() -> {
                start.await();
                try {
                    tx.executeWithoutResult(s -> inventory.reserve(Map.of(product, 1)));
                    return true;
                } catch (InsufficientStockException e) {
                    return false;
                }
            });
        }

        List<Boolean> outcomes = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(buyers)) {
            List<Future<Boolean>> futures = tasks.stream().map(pool::submit).toList();
            start.countDown();
            for (Future<Boolean> f : futures) {
                outcomes.add(f.get());
            }
        }

        assertThat(outcomes).filteredOn(Boolean::booleanValue).hasSize(5);
        assertThat(TestData.stockOf(jdbc, product)).isZero();
    }

    @Test
    void reservationIsAllOrNothing() {
        UUID plenty = TestData.insertProduct(jdbc, "Abundante", new BigDecimal("1.00"), 50);
        UUID scarce = TestData.insertProduct(jdbc, "Escaso", new BigDecimal("1.00"), 1);

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> inventory.reserve(Map.of(plenty, 10, scarce, 2))))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Escaso").hasMessageContaining("available: 1");

        assertThat(TestData.stockOf(jdbc, plenty)).isEqualTo(50);   // rolled back
        assertThat(TestData.stockOf(jdbc, scarce)).isEqualTo(1);
    }

    @Test
    void releaseGivesStockBack() {
        UUID product = TestData.insertProduct(jdbc, "Devuelto", new BigDecimal("1.00"), 4);

        tx.executeWithoutResult(s -> inventory.reserve(Map.of(product, 3)));
        inventory.release(Map.of(product, 3));

        assertThat(TestData.stockOf(jdbc, product)).isEqualTo(4);
    }

    @Test
    void reserveMustRunInsideTheCallersTransaction() {
        UUID product = TestData.insertProduct(jdbc, "Sin tx", new BigDecimal("1.00"), 4);

        assertThatThrownBy(() -> inventory.reserve(Map.of(product, 1)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void unknownProductIsNotFound() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> inventory.reserve(Map.of(UUID.randomUUID(), 1))))
                .hasFieldOrPropertyWithValue("code", "PRODUCT_NOT_FOUND");
    }
}
