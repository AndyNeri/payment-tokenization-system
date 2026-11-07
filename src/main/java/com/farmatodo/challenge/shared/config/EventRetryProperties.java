package com.farmatodo.challenge.shared.config;

import java.time.Duration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Retry of failed asynchronous listeners (e.g. SMTP down when the payment email was due).
 *
 * @param interval    how often failed event publications are looked for
 * @param minAge      only publications that failed at least this long ago are resubmitted (gives the
 *                    dependency time to recover)
 * @param maxAttempts give up after this many attempts: the row stays FAILED in {@code event_publication}
 *                    for manual inspection instead of retrying a poison message forever
 */
@Validated
@ConfigurationProperties("app.events.retry")
public record EventRetryProperties(@NotNull Duration interval, @NotNull Duration minAge, @Min(1) @Max(20) int maxAttempts) {
}
