package com.farmatodo.challenge.customer.infrastructure.out.persistence;

import java.util.UUID;

import com.farmatodo.challenge.customer.CustomerDirectory;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Minimal implementation of the customer module API; the customer slice owns the {@code customers} table. */
@Component
class JdbcCustomerDirectory implements CustomerDirectory {

    private final JdbcClient jdbc;

    JdbcCustomerDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean exists(UUID customerId) {
        return jdbc.sql("select exists(select 1 from customers where id = :id)")
                .param("id", customerId)
                .query(Boolean.class)
                .single();
    }
}
