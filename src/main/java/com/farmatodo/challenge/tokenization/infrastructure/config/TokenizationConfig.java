package com.farmatodo.challenge.tokenization.infrastructure.config;

import java.util.concurrent.ThreadLocalRandom;

import com.farmatodo.challenge.tokenization.domain.RandomProvider;
import com.farmatodo.challenge.tokenization.domain.RejectionPolicy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the framework-free domain objects of the tokenization slice. */
@Configuration(proxyBeanMethods = false)
class TokenizationConfig {

    /**
     * Business randomness only (not security): ThreadLocalRandom is fast and contention-free under concurrency.
     * Tokens and IVs use SecureRandom instead.
     */
    @Bean
    RandomProvider tokenizationRandom() {
        return () -> ThreadLocalRandom.current().nextDouble();
    }

    @Bean
    RejectionPolicy tokenizationRejectionPolicy(TokenizationProperties properties, RandomProvider tokenizationRandom) {
        return new RejectionPolicy(properties.rejectionProbability(), tokenizationRandom);
    }
}
