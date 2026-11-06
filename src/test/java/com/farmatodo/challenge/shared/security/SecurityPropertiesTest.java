package com.farmatodo.challenge.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Base64;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

class SecurityPropertiesTest {

    static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    static final String STRONG_KEY = "k".repeat(40);
    static final String STRONG_SECRET = Base64.getEncoder().encodeToString(new byte[32]);

    static SecurityProperties props(String apiKey, String secret) {
        return new SecurityProperties(apiKey, new SecurityProperties.Jwt(secret, Duration.ofMinutes(30), "issuer"));
    }

    @Test
    void acceptsStrongConfiguration() {
        assertThat(VALIDATOR.validate(props(STRONG_KEY, STRONG_SECRET))).isEmpty();
        assertThat(props(STRONG_KEY, STRONG_SECRET).jwt().secretBytes()).hasSize(32);
    }

    @Test
    void rejectsShortApiKey() {
        assertThat(VALIDATOR.validate(props("short", STRONG_SECRET))).isNotEmpty();
    }

    @Test
    void rejectsUnresolvedPlaceholderAsApiKey() {
        assertThat(VALIDATOR.validate(props("${API_KEY}-padding-to-reach-32-chars", STRONG_SECRET)))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("apiKeyResolved");
    }

    @Test
    void rejectsWeakOrNonBase64JwtSecret() {
        String weak = Base64.getEncoder().encodeToString(new byte[16]);
        assertThat(VALIDATOR.validate(props(STRONG_KEY, weak))).isNotEmpty();
        assertThat(VALIDATOR.validate(props(STRONG_KEY, "%%%not-base64%%%"))).isNotEmpty();
    }
}
