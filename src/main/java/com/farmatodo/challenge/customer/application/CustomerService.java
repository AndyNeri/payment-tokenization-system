package com.farmatodo.challenge.customer.application;

import java.time.Clock;
import java.util.UUID;

import com.farmatodo.challenge.customer.CustomerRegistered;
import com.farmatodo.challenge.customer.domain.ContactNormalizer;
import com.farmatodo.challenge.customer.domain.Customer;
import com.farmatodo.challenge.customer.domain.CustomerPorts.CustomerRepository;
import com.farmatodo.challenge.customer.domain.CustomerPorts.DuplicateCustomerException;
import com.farmatodo.challenge.customer.domain.CustomerPorts.PasswordHasher;
import com.farmatodo.challenge.shared.error.Exceptions.NotFoundException;
import com.farmatodo.challenge.shared.tracing.TransactionContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customer registration and lookup. */
@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customers;
    private final PasswordHasher passwordHasher;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    CustomerService(CustomerRepository customers, PasswordHasher passwordHasher, ApplicationEventPublisher events,
            Clock clock) {
        this.customers = customers;
        this.passwordHasher = passwordHasher;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Registers a customer. Uniqueness is checked twice on purpose:
     * first here, for a clear error message; then by the database UNIQUE constraints, which are the only real
     * guarantee when two identical registrations arrive at the same time.
     */
    @Transactional
    public Customer register(RegisterCustomerCommand command) {
        String email = ContactNormalizer.email(command.email());
        String phone = ContactNormalizer.phone(command.phone());

        if (customers.existsByEmail(email)) {
            throw DuplicateCustomerException.email();
        }
        if (customers.existsByPhone(phone)) {
            throw DuplicateCustomerException.phone();
        }

        Customer saved = customers.save(new Customer(null, command.firstName().strip(), command.lastName().strip(),
                email, phone, command.address().strip(), passwordHasher.hash(command.password()), clock.instant()));

        log.info("Customer registered: {}", saved.id());
        events.publishEvent(new CustomerRegistered(saved.id(), TransactionContext.currentOrNew(), saved.createdAt()));
        return saved;
    }

    @Transactional(readOnly = true)
    public Customer get(UUID id) {
        return customers.findById(id)
                .orElseThrow(() -> new NotFoundException("CUSTOMER_NOT_FOUND", "Customer " + id + " does not exist"));
    }

    public record RegisterCustomerCommand(String firstName, String lastName, String email, String phone,
                                          String address, String password) {
        @Override
        public String toString() {
            return "RegisterCustomerCommand[email=" + email + ", password=***]";
        }
    }
}
