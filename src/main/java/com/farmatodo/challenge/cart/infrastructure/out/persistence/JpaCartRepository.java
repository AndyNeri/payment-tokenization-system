package com.farmatodo.challenge.cart.infrastructure.out.persistence;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.farmatodo.challenge.cart.domain.Cart;
import com.farmatodo.challenge.cart.domain.Cart.CartItem;
import com.farmatodo.challenge.cart.domain.Cart.CartRepository;
import com.farmatodo.challenge.cart.infrastructure.out.persistence.CartJpaEntity.CartItemJpaEntity;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

/**
 * Maps the domain cart onto the JPA aggregate, updating rows in place (instead of delete + insert)
 * so the UNIQUE(cart_id, product_id) constraint is never hit by a re-insert within the same flush.
 */
@Component
class JpaCartRepository implements CartRepository {

    private final CartJpaRepository repository;
    private final Clock clock;

    JpaCartRepository(CartJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Optional<Cart> findByCustomer(UUID customerId) {
        return repository.findByCustomerId(customerId).map(JpaCartRepository::toDomain);
    }

    @Override
    public Cart save(Cart cart) {
        CartJpaEntity entity = repository.findByCustomerId(cart.customerId())
                .orElseGet(() -> new CartJpaEntity(cart.customerId()));
        Map<UUID, CartItem> desired = cart.items().stream()
                .collect(Collectors.toMap(CartItem::productId, Function.identity()));

        entity.getItems().removeIf(row -> !desired.containsKey(row.getProductId()));
        for (CartItem item : desired.values()) {
            entity.getItems().stream()
                    .filter(row -> row.getProductId().equals(item.productId()))
                    .findFirst()
                    .ifPresentOrElse(row -> row.setQuantity(item.quantity()),
                            () -> entity.getItems().add(new CartItemJpaEntity(entity, item.productId(), item.quantity())));
        }
        entity.touch(clock.instant());
        return toDomain(repository.saveAndFlush(entity));
    }

    private static Cart toDomain(CartJpaEntity entity) {
        return new Cart(entity.getId(), entity.getCustomerId(), entity.getItems().stream()
                .map(row -> new CartItem(row.getProductId(), row.getQuantity()))
                .toList());
    }
}

interface CartJpaRepository extends JpaRepository<CartJpaEntity, UUID> {

    /** Loads cart + items in one query (avoids N+1). */
    @EntityGraph(attributePaths = "items")
    Optional<CartJpaEntity> findByCustomerId(UUID customerId);
}
