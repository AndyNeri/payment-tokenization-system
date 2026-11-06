package com.farmatodo.challenge.shared.config;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Asynchronous execution for event listeners ({@code @ApplicationModuleListener} is {@code @Async}).
 * <ul>
 *   <li>Spring Boot's auto-configured executor runs on virtual threads ({@code spring.threads.virtual.enabled}).</li>
 *   <li>The {@link TaskDecorator} copies the logging MDC (transactionId) into the async thread, so logs written by
 *       listeners can still be correlated with the request that triggered them.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfig {

    @Bean
    TaskDecorator mdcPropagatingTaskDecorator() {
        return runnable -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (context != null) {
                    MDC.setContextMap(context);
                }
                try {
                    runnable.run();
                } finally {
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                }
            };
        };
    }
}
