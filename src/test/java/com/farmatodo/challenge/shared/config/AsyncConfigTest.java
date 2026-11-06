package com.farmatodo.challenge.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class AsyncConfigTest {

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void propagatesMdcToTheAsyncThreadAndRestoresIt() throws Exception {
        var decorator = new AsyncConfig().mdcPropagatingTaskDecorator();
        MDC.put("transactionId", "abc-123");
        AtomicReference<String> seen = new AtomicReference<>();
        Runnable decorated = decorator.decorate(() -> seen.set(MDC.get("transactionId")));
        MDC.clear();

        Thread worker = Thread.ofVirtual().start(decorated);
        worker.join();

        assertThat(seen.get()).isEqualTo("abc-123");
    }

    @Test
    void worksWithoutContextAndRestoresPreviousValues() {
        var decorator = new AsyncConfig().mdcPropagatingTaskDecorator();
        Runnable decorated = decorator.decorate(() -> assertThat(MDC.get("transactionId")).isNull());

        decorated.run();
        MDC.put("transactionId", "outer");
        decorator.decorate(() -> MDC.put("transactionId", "inner")).run();

        assertThat(MDC.get("transactionId")).isEqualTo("outer");
    }
}
