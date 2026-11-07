package com.farmatodo.challenge;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/** Test fixtures inserted straight into the database (fast, independent of other modules' APIs). */
public final class TestData {

    private TestData() {
    }

    public static UUID insertCustomer(JdbcTemplate jdbc) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into customers (id, first_name, last_name, email, phone, address, password_hash) "
                        + "values (?, 'Test', 'User', ?, ?, 'Calle 1', '{bcrypt}x')",
                id, "user-" + id + "@test.dev", "+58" + Math.floorMod(id.getMostSignificantBits(), 10_000_000_000L));
        return id;
    }

    public static UUID insertProduct(JdbcTemplate jdbc, String name, BigDecimal price, int stock) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into products (id, sku, name, description, price, stock) values (?, ?, ?, ?, ?, ?)",
                id, "TST-" + id.toString().substring(0, 8), name, "test product", price, stock);
        return id;
    }

    public static int stockOf(JdbcTemplate jdbc, UUID productId) {
        return jdbc.queryForObject("select stock from products where id = ?", Integer.class, productId);
    }

    /** Polls a count query until it reaches {@code expected} (async listeners) or 10 s pass; returns the last value. */
    public static int awaitCount(JdbcTemplate jdbc, int expected, String sql, Object... args) throws InterruptedException {
        java.time.Instant deadline = java.time.Instant.now().plusSeconds(10);
        int count = 0;
        while (java.time.Instant.now().isBefore(deadline)) {
            Integer value = jdbc.queryForObject(sql, Integer.class, args);
            count = value == null ? 0 : value;
            if (count >= expected) {
                return count;
            }
            Thread.sleep(100);
        }
        return count;
    }
}
