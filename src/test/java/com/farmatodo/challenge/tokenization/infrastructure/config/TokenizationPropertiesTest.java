package com.farmatodo.challenge.tokenization.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.Map;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

class TokenizationPropertiesTest {

    static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    static final String KEY_32 = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void probabilityMustBeWithinZeroAndOne() {
        assertThat(VALIDATOR.validate(new TokenizationProperties(0.0))).isEmpty();
        assertThat(VALIDATOR.validate(new TokenizationProperties(1.0))).isEmpty();
        assertThat(VALIDATOR.validate(new TokenizationProperties(1.5))).isNotEmpty();
        assertThat(VALIDATOR.validate(new TokenizationProperties(-0.1))).isNotEmpty();
    }

    @Test
    void cryptoAcceptsValidAes256Keys() {
        var props = new CryptoProperties("k1", Map.of("k1", KEY_32));
        assertThat(VALIDATOR.validate(props)).isEmpty();
        assertThat(props.keyBytes("k1")).hasSize(32);
    }

    @Test
    void cryptoRejectsShortNonBase64OrUnresolvedKeys() {
        String key16 = Base64.getEncoder().encodeToString(new byte[16]);
        assertThat(VALIDATOR.validate(new CryptoProperties("k1", Map.of("k1", key16)))).isNotEmpty();
        assertThat(VALIDATOR.validate(new CryptoProperties("k1", Map.of("k1", "${CARD_ENCRYPTION_KEY}")))).isNotEmpty();
    }

    @Test
    void cryptoRequiresActiveKeyToExist() {
        var props = new CryptoProperties("k2", Map.of("k1", KEY_32));
        assertThat(VALIDATOR.validate(props)).isNotEmpty();
        assertThatThrownBy(() -> props.keyBytes("k2")).isInstanceOf(IllegalStateException.class);
    }
}
