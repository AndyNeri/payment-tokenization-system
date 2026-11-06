package com.farmatodo.challenge.tokenization.infrastructure.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param rejectionProbability probability in [0, 1] that a valid tokenization request is rejected (ADR-007).
 *                             Out-of-range values stop the application at startup.
 */
@Validated
@ConfigurationProperties("app.tokenization")
public record TokenizationProperties(
        @DecimalMin("0.0") @DecimalMax("1.0") double rejectionProbability) {
}
