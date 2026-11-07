package com.farmatodo.challenge.shared.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Periodically resubmits event publications whose listener failed (outbox pattern, ADR-009): an email that could
 * not be sent because SMTP was down is retried automatically, without waiting for a restart.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class EventRetryConfig implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(EventRetryConfig.class);

    private final FailedEventPublications failedPublications;
    private final EventRetryProperties properties;

    EventRetryConfig(FailedEventPublications failedPublications, EventRetryProperties properties) {
        this.failedPublications = failedPublications;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(this::resubmitFailed, properties.interval());
    }

    void resubmitFailed() {
        try {
            failedPublications.resubmit(ResubmissionOptions.defaults()
                    .withMinAge(properties.minAge())
                    .withFilter(publication -> publication.getCompletionAttempts() < properties.maxAttempts()));
        } catch (RuntimeException e) {
            log.warn("Could not resubmit failed event publications: {}", e.getMessage());
        }
    }
}
