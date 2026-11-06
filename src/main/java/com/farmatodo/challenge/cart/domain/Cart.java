package com.farmatodo.challenge.cart.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.farmatodo.challenge.shared.error.DomainException.FieldError;
import com.farmatodo.challenge.shared.error.Exceptions.InvalidInputException;
import com.farmatodo.challenge.shared.error.Exceptions.NotFoundException;

/**
 * A customer's shopping cart: one line per product (adding the same product again increases its quantity).
 * Prices are not stored here: the cart always shows current catalog prices; the price is frozen only when an
 * order is placed.
 */
public final class Cart {

    public static final int MAX_QUANTITY_PER_PRODUCT = 99;

    private final UUID id;
    private final UUID customerId;
    private final List<CartItem> items;

    public Cart(UUID id, UUID customerId, List<CartItem> items) {
        this.id = id;
        this.customerId = customerId;
        this.items = new ArrayList<>(items);
    }

    public static Cart empty(UUID customerId) {
        return new Cart(null, customerId, List.of());
    }

    /** @return the resulting quantity for that product */
    public int add(UUID productId, int quantity) {
        if (quantity < 1) {
            throw invalidQuantity("must be at least 1");
        }
        int newQuantity = quantityOf(productId) + quantity;
        if (newQuantity > MAX_QUANTITY_PER_PRODUCT) {
            throw invalidQuantity("cannot exceed " + MAX_QUANTITY_PER_PRODUCT + " units per product");
        }
        items.removeIf(item -> item.productId().equals(productId));
        items.add(new CartItem(productId, newQuantity));
        return newQuantity;
    }

    public void remove(UUID productId) {
        if (!items.removeIf(item -> item.productId().equals(productId))) {
            throw new NotFoundException("CART_ITEM_NOT_FOUND", "Product " + productId + " is not in the cart");
        }
    }

    public void clear() {
        items.clear();
    }

    public int quantityOf(UUID productId) {
        return find(productId).map(CartItem::quantity).orElse(0);
    }

    public Optional<CartItem> find(UUID productId) {
        return items.stream().filter(item -> item.productId().equals(productId)).findFirst();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public UUID id() {
        return id;
    }

    public UUID customerId() {
        return customerId;
    }

    public List<CartItem> items() {
        return Collections.unmodifiableList(items);
    }

    private static InvalidInputException invalidQuantity(String message) {
        return new InvalidInputException("VALIDATION_ERROR", "Invalid quantity",
                List.of(new FieldError("quantity", message)));
    }

    public record CartItem(UUID productId, int quantity) {
    }

    /** Outbound port. */
    public interface CartRepository {
        Optional<Cart> findByCustomer(UUID customerId);

        Cart save(Cart cart);
    }
}
