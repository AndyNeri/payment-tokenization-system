package com.farmatodo.challenge.shared.security;

import java.time.Duration;
import java.util.Base64;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Security settings. Values come only from environment variables / Secret Manager (no defaults for secrets).
 * Validated at startup: the application refuses to start with missing or weak secrets (fail-fast).
 */
@Validated
@ConfigurationProperties("app.security")
public record SecurityProperties(
        @NotBlank @Size(min = 32, message = "must be at least 32 characters") String apiKey,
        @NotNull @Valid Jwt jwt) {

    /** Guards against an unresolved placeholder (e.g. "${API_KEY}") silently becoming the real key. */
    @AssertTrue(message = "app.security.api-key looks like an unresolved placeholder; set the API_KEY variable")
    boolean isApiKeyResolved() {
        return apiKey == null || !apiKey.startsWith("${");
    }

    public record Jwt(
            @NotBlank String secret,
            @NotNull Duration ttl,
            @NotBlank String issuer) {

        /** HS256 needs a key of at least 256 bits (RFC 7518 §3.2). The secret is Base64-encoded. */
        @AssertTrue(message = "app.security.jwt.secret must be Base64 and decode to at least 32 bytes (256 bits)")
        boolean isSecretStrongEnough() {
            if (secret == null) {
                return true; // reported by @NotBlank
            }
            try {
                return Base64.getDecoder().decode(secret).length >= 32;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        public byte[] secretBytes() {
            return Base64.getDecoder().decode(secret);
        }
    }
}
