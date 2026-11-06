package com.farmatodo.challenge.customer.application;

import java.util.Optional;
import java.util.UUID;

import com.farmatodo.challenge.customer.CustomerAuthenticator;
import com.farmatodo.challenge.customer.CustomerDirectory;
import com.farmatodo.challenge.customer.domain.ContactNormalizer;
import com.farmatodo.challenge.customer.domain.Customer;
import com.farmatodo.challenge.customer.domain.CustomerPorts.CustomerRepository;
import com.farmatodo.challenge.customer.domain.CustomerPorts.PasswordHasher;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementation of the customer module's public API used by other modules (tokenization, auth). */
@Service
class CustomerModuleApi implements CustomerDirectory, CustomerAuthenticator {

    private final CustomerRepository customers;
    private final PasswordHasher passwordHasher;
    private final String dummyHash;

    CustomerModuleApi(CustomerRepository customers, PasswordHasher passwordHasher) {
        this.customers = customers;
        this.passwordHasher = passwordHasher;
        this.dummyHash = passwordHasher.hash("timing-equalizer-not-a-real-password");
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID customerId) {
        return customers.existsById(customerId);
    }

    /**
     * When the email does not exist we still run a BCrypt comparison against a dummy hash, so the response time
     * is the same as for a wrong password: timing does not reveal which emails are registered (user enumeration).
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> authenticate(String email, String rawPassword) {
        if (email == null || rawPassword == null) {
            return Optional.empty();
        }
        Optional<Customer> customer = customers.findByEmail(ContactNormalizer.email(email));
        String hash = customer.map(Customer::passwordHash).orElse(dummyHash);
        boolean matches = passwordHasher.matches(rawPassword, hash);
        return customer.filter(c -> matches).map(Customer::id);
    }
}
