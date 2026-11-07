package com.farmatodo.challenge.order.application;

import java.time.Duration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Payment simulation settings (challenge requirement: configurable probability and N retries).
 *
 * @param rejectionProbability probability in [0, 1] that a single payment attempt is rejected (r &lt; p, ADR-007)
 * @param maxRetries           retries after the first attempt: total attempts = 1 + maxRetries
 * @param retryBackoff         base wait between attempts; doubles each retry (exponential backoff)
 */
@Validated
@ConfigurationProperties("app.payment")
public record PaymentProperties(
        @DecimalMin("0.0") @DecimalMax("1.0") double rejectionProbability,
        @Min(0) @Max(10) int maxRetries,
        @NotNull Duration retryBackoff) {

    /** The checkout is synchronous (ADR-008): keep the worst-case wait bounded. */
    @AssertTrue(message = "app.payment.retry-backoff must be between 0 and 5s")
    boolean isBackoffReasonable() {
        return retryBackoff == null || (!retryBackoff.isNegative() && retryBackoff.compareTo(Duration.ofSeconds(5)) <= 0);
    }

    public int totalAttempts() {
        return 1 + maxRetries;
    }

    /** base, 2·base, 4·base... before attempt 2, 3, 4... */
    public Duration backoffBefore(int nextAttempt) {
        return retryBackoff.multipliedBy(1L << Math.max(0, nextAttempt - 2));
    }
}
