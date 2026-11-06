package com.farmatodo.challenge;

import org.springframework.boot.SpringApplication;

/**
 * Runs the app from the IDE with a throw-away PostgreSQL container and the test profile (no .env needed).
 * For the full local stack (app + postgres + mailpit) use docker compose instead.
 */
public class TestPaymentTokenizationSystemApplication {

    public static void main(String[] args) {
        SpringApplication.from(PaymentTokenizationSystemApplication::main)
                .with(TestcontainersConfiguration.class)
                .withAdditionalProfiles("test")
                .run(args);
    }
}
