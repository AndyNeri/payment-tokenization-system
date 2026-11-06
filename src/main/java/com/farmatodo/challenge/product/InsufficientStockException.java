package com.farmatodo.challenge.product;

import java.util.UUID;

import com.farmatodo.challenge.shared.error.Exceptions.ConflictException;

/** 409: not enough units of a product. Part of the product module API (thrown to cart and order). */
public class InsufficientStockException extends ConflictException {

    private final UUID productId;

    public InsufficientStockException(UUID productId, String productName, int available) {
        super("INSUFFICIENT_STOCK", "Not enough stock for '" + productName + "' (available: " + available + ")");
        this.productId = productId;
    }

    public UUID productId() {
        return productId;
    }
}
