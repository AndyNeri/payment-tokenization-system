package com.farmatodo.challenge.product.infrastructure.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "search_logs")
class SearchLogJpaEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(length = 100)
    private String query;

    @Column(name = "results_count", nullable = false)
    private int resultsCount;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SearchLogJpaEntity() {
        // for JPA
    }

    SearchLogJpaEntity(String query, int resultsCount, UUID customerId, UUID transactionId, Instant createdAt) {
        this.query = query;
        this.resultsCount = resultsCount;
        this.customerId = customerId;
        this.transactionId = transactionId;
        this.createdAt = createdAt;
    }
}
