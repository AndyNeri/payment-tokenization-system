package com.farmatodo.challenge;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real infrastructure for integration tests: PostgreSQL 18 (same major version as Cloud SQL; no H2, ADR-003) and
 * Mailpit (the same fake SMTP server as docker compose), so emails really travel over SMTP and can be asserted
 * through Mailpit's HTTP API.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String MAILPIT_API_PROPERTY = "test.mailpit.api-url";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    }

    @Bean
    GenericContainer<?> mailpitContainer() {
        return new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.33"))
                .withExposedPorts(1025, 8025)
                .waitingFor(Wait.forHttp("/livez").forPort(8025));
    }

    @Bean
    DynamicPropertyRegistrar mailpitProperties(GenericContainer<?> mailpitContainer) {
        return registry -> {
            registry.add("spring.mail.host", mailpitContainer::getHost);
            registry.add("spring.mail.port", () -> mailpitContainer.getMappedPort(1025));
            registry.add(MAILPIT_API_PROPERTY,
                    () -> "http://" + mailpitContainer.getHost() + ":" + mailpitContainer.getMappedPort(8025));
        };
    }
}
