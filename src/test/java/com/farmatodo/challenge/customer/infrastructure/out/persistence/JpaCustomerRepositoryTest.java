package com.farmatodo.challenge.customer.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.farmatodo.challenge.customer.domain.CustomerPorts.DuplicateCustomerException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class JpaCustomerRepositoryTest {

    @Test
    void translatesUniqueViolationsByConstraintName() {
        assertThat(JpaCustomerRepository.translate(violation("duplicate key value violates unique constraint \"uq_customers_email\"")))
                .isInstanceOf(DuplicateCustomerException.class)
                .hasFieldOrPropertyWithValue("code", "EMAIL_ALREADY_EXISTS");
        assertThat(JpaCustomerRepository.translate(violation("... \"uq_customers_phone\"")))
                .hasFieldOrPropertyWithValue("code", "PHONE_ALREADY_EXISTS");
    }

    @Test
    void unknownViolationsAreRethrownAsIs() {
        DataIntegrityViolationException other = violation("fk_something");
        assertThat(JpaCustomerRepository.translate(other)).isSameAs(other);
    }

    private static DataIntegrityViolationException violation(String message) {
        return new DataIntegrityViolationException("wrapper", new RuntimeException(message));
    }
}
