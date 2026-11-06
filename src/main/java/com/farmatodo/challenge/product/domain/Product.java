package com.farmatodo.challenge.product.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Catalog item. Price uses BigDecimal: money is never a floating-point number. */
public record Product(UUID id, String sku, String name, String description, BigDecimal price, int stock) {
}
