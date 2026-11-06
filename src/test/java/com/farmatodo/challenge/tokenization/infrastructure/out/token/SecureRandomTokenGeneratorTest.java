package com.farmatodo.challenge.tokenization.infrastructure.out.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SecureRandomTokenGeneratorTest {

    final SecureRandomTokenGenerator generator = new SecureRandomTokenGenerator();

    @Test
    void tokenHasPrefixUrlSafeAlphabetAndFitsTheColumn() {
        String token = generator.generate();

        assertThat(token).startsWith("tok_").matches("tok_[A-Za-z0-9_-]{32}");
        assertThat(token.length()).isLessThanOrEqualTo(40);
    }

    @Test
    void tokensAreUnique() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            tokens.add(generator.generate());
        }
        assertThat(tokens).hasSize(10_000);
    }
}
