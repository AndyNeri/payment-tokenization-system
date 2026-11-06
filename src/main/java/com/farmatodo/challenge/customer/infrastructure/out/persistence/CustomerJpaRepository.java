package com.farmatodo.challenge.customer.infrastructure.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface CustomerJpaRepository extends JpaRepository<CustomerJpaEntity, UUID> {

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    Optional<CustomerJpaEntity> findByEmail(String email);
}
