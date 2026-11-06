package com.farmatodo.challenge.tokenization.infrastructure.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface CardJpaRepository extends JpaRepository<CardJpaEntity, UUID> {

    Optional<CardJpaEntity> findByToken(String token);

    List<CardJpaEntity> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}
