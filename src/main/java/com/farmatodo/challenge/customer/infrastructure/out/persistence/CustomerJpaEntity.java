package com.farmatodo.challenge.customer.infrastructure.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.farmatodo.challenge.customer.domain.Customer;

import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "customers")
class CustomerJpaEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(nullable = false)
    private String address;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CustomerJpaEntity() {
        // for JPA
    }

    static CustomerJpaEntity from(Customer c) {
        CustomerJpaEntity e = new CustomerJpaEntity();
        e.firstName = c.firstName();
        e.lastName = c.lastName();
        e.email = c.email();
        e.phone = c.phone();
        e.address = c.address();
        e.passwordHash = c.passwordHash();
        e.createdAt = c.createdAt();
        return e;
    }

    Customer toDomain() {
        return new Customer(id, firstName, lastName, email, phone, address, passwordHash, createdAt);
    }
}
