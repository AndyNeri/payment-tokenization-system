package com.farmatodo.challenge.cart.infrastructure.out.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "carts")
class CartJpaEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "customer_id", nullable = false, unique = true, updatable = false)
    private UUID customerId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Items live and die with the cart (cascade + orphanRemoval). */
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CartItemJpaEntity> items = new ArrayList<>();

    protected CartJpaEntity() {
        // for JPA
    }

    CartJpaEntity(UUID customerId) {
        this.customerId = customerId;
    }

    UUID getId() {
        return id;
    }

    UUID getCustomerId() {
        return customerId;
    }

    List<CartItemJpaEntity> getItems() {
        return items;
    }

    void touch(Instant now) {
        this.updatedAt = now;
    }

    @Entity
    @Table(name = "cart_items")
    static class CartItemJpaEntity {

        @Id
        @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
        private UUID id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "cart_id", nullable = false)
        private CartJpaEntity cart;

        @Column(name = "product_id", nullable = false)
        private UUID productId;

        @Column(nullable = false)
        private int quantity;

        protected CartItemJpaEntity() {
            // for JPA
        }

        CartItemJpaEntity(CartJpaEntity cart, UUID productId, int quantity) {
            this.cart = cart;
            this.productId = productId;
            this.quantity = quantity;
        }

        UUID getProductId() {
            return productId;
        }

        int getQuantity() {
            return quantity;
        }

        void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }
}
