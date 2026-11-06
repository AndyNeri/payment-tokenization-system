package com.farmatodo.challenge.customer.domain;

import java.util.Optional;
import java.util.UUID;

import com.farmatodo.challenge.shared.error.Exceptions.ConflictException;

/** Outbound ports of the customer slice. */
public final class CustomerPorts {

    private CustomerPorts() {
    }

    public interface CustomerRepository {

        /**
         * Persists a new customer.
         *
         * @throws DuplicateCustomerException if the email or phone is already taken (also under a race condition:
         *                                    the adapter translates the database UNIQUE violation)
         */
        Customer save(Customer customer);

        boolean existsByEmail(String normalizedEmail);

        boolean existsByPhone(String normalizedPhone);

        boolean existsById(UUID id);

        Optional<Customer> findById(UUID id);

        Optional<Customer> findByEmail(String normalizedEmail);
    }

    /** One-way password hashing (BCrypt behind the adapter). */
    public interface PasswordHasher {
        String hash(String rawPassword);

        boolean matches(String rawPassword, String hash);
    }

    /** 409 for a taken email or phone. */
    public static class DuplicateCustomerException extends ConflictException {

        public static DuplicateCustomerException email() {
            return new DuplicateCustomerException("EMAIL_ALREADY_EXISTS", "A customer with this email already exists");
        }

        public static DuplicateCustomerException phone() {
            return new DuplicateCustomerException("PHONE_ALREADY_EXISTS", "A customer with this phone already exists");
        }

        private DuplicateCustomerException(String code, String message) {
            super(code, message);
        }
    }
}
