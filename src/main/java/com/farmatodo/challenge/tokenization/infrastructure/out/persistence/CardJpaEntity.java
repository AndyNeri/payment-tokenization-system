package com.farmatodo.challenge.tokenization.infrastructure.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.farmatodo.challenge.tokenization.domain.CardBrand;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** JPA mapping of the {@code cards} table. Lives in infrastructure: the domain never sees it. */
@Entity
@Table(name = "cards")
class CardJpaEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, unique = true, length = 40, updatable = false)
    private String token;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "encrypted_payload", nullable = false)
    private byte[] encryptedPayload;

    @Column(nullable = false)
    private byte[] iv;

    @Column(name = "key_id", nullable = false, length = 20)
    private String keyId;

    @Column(nullable = false, length = 4)
    @JdbcTypeCode(SqlTypes.CHAR)    // column is char(4), not varchar (checked by ddl-auto=validate)
    private String last4;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardBrand brand;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CardJpaEntity() {
        // for JPA
    }

    CardJpaEntity(String token, UUID customerId, byte[] encryptedPayload, byte[] iv, String keyId, String last4,
            CardBrand brand, Instant createdAt) {
        this.token = token;
        this.customerId = customerId;
        this.encryptedPayload = encryptedPayload;
        this.iv = iv;
        this.keyId = keyId;
        this.last4 = last4;
        this.brand = brand;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    String getToken() {
        return token;
    }

    UUID getCustomerId() {
        return customerId;
    }

    byte[] getEncryptedPayload() {
        return encryptedPayload;
    }

    byte[] getIv() {
        return iv;
    }

    String getKeyId() {
        return keyId;
    }

    String getLast4() {
        return last4;
    }

    CardBrand getBrand() {
        return brand;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
