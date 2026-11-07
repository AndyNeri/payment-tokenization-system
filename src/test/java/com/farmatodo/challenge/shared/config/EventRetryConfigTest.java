package com.farmatodo.challenge.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

class EventRetryConfigTest {

    FailedEventPublications failed = mock(FailedEventPublications.class);
    EventRetryConfig config = new EventRetryConfig(failed,
            new EventRetryProperties(Duration.ofMinutes(1), Duration.ofSeconds(30), 3));

    @Test
    void resubmitsFailedPublicationsOldEnoughAndBelowTheAttemptLimit() {
        config.resubmitFailed();

        ArgumentCaptor<ResubmissionOptions> captor = ArgumentCaptor.forClass(ResubmissionOptions.class);
        verify(failed).resubmit(captor.capture());
        ResubmissionOptions options = captor.getValue();
        assertThat(options.getMinAge()).isEqualTo(Duration.ofSeconds(30));
        assertThat(options.getFilter().test(publicationWithAttempts(2))).isTrue();
        assertThat(options.getFilter().test(publicationWithAttempts(3))).isFalse();   // poison: give up
    }

    @Test
    void aFailingResubmissionNeverBreaksTheScheduler() {
        doThrow(new IllegalStateException("db down")).when(failed).resubmit(any());

        assertThatNoException().isThrownBy(config::resubmitFailed);
    }

    @Test
    void registersAFixedDelayTask() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        config.configureTasks(registrar);

        assertThat(registrar.getFixedDelayTaskList()).singleElement()
                .satisfies(task -> assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofMinutes(1)));
    }

    private static EventPublication publicationWithAttempts(int attempts) {
        EventPublication publication = mock(EventPublication.class);
        when(publication.getCompletionAttempts()).thenReturn(attempts);
        return publication;
    }
}
