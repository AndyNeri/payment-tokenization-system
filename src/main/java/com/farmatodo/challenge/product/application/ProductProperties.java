package com.farmatodo.challenge.product.application;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param minStock only products with {@code stock > minStock} are shown in searches (challenge requirement).
 *                 0 hides out-of-stock items; e.g. 3 also hides "last units" items.
 */
@Validated
@ConfigurationProperties("app.product")
public record ProductProperties(@Min(0) int minStock) {
}
