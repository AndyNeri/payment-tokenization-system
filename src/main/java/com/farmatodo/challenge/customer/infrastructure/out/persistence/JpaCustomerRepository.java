package com.farmatodo.challenge.customer.infrastructure.out.persistence;

import java.util.Optional;
import java.util.UUID;

import com.farmatodo.challenge.customer.domain.Customer;
import com.farmatodo.challenge.customer.domain.CustomerPorts.CustomerRepository;
import com.farmatodo.challenge.customer.domain.CustomerPorts.DuplicateCustomerException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
class JpaCustomerRepository implements CustomerRepository {

    static final String UQ_EMAIL = "uq_customers_email";
    static final String UQ_PHONE = "uq_customers_phone";

    private final CustomerJpaRepository repository;

    JpaCustomerRepository(CustomerJpaRepository repository) {
        this.repository = repository;
    }

    /**
     * {@code saveAndFlush} forces the INSERT now (instead of at commit), so a UNIQUE violation caused by a
     * concurrent registration surfaces here and is translated into the same 409 as the pre-check.
     */
    @Override
    public Customer save(Customer customer) {
        try {
            return repository.saveAndFlush(CustomerJpaEntity.from(customer)).toDomain();
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
    }

    @Override
    public boolean existsByEmail(String normalizedEmail) {
        return repository.existsByEmail(normalizedEmail);
    }

    @Override
    public boolean existsByPhone(String normalizedPhone) {
        return repository.existsByPhone(normalizedPhone);
    }

    @Override
    public boolean existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return repository.findById(id).map(CustomerJpaEntity::toDomain);
    }

    @Override
    public Optional<Customer> findByEmail(String normalizedEmail) {
        return repository.findByEmail(normalizedEmail).map(CustomerJpaEntity::toDomain);
    }

    static RuntimeException translate(DataIntegrityViolationException e) {
        String detail = String.valueOf(e.getMostSpecificCause().getMessage());
        if (detail.contains(UQ_EMAIL)) {
            return DuplicateCustomerException.email();
        }
        if (detail.contains(UQ_PHONE)) {
            return DuplicateCustomerException.phone();
        }
        return e;
    }
}
