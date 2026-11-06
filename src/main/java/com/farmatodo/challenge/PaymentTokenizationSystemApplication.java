package com.farmatodo.challenge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Modular monolith entry point. Each top-level package below this one is an application module
 * (vertical slice) verified by Spring Modulith; see docs/architecture.md.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PaymentTokenizationSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentTokenizationSystemApplication.class, args);
    }
}
