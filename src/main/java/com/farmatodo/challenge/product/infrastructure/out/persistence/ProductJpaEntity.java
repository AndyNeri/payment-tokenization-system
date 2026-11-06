package com.farmatodo.challenge.product.infrastructure.out.persistence;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.farmatodo.challenge.product.domain.Product;

@Entity
@Table(name = "products")
class ProductJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String sku;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int stock;

    @Version
    private long version;

    protected ProductJpaEntity() {
        // for JPA
    }

    Product toDomain() {
        return new Product(id, sku, name, description, price, stock);
    }
}
