package com.farmatodo.challenge.tokenization.infrastructure.config;

import java.util.Base64;
import java.util.Map;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * AES-256 keys for card encryption, indexed by key id to support rotation:
 * new data is encrypted with {@code activeKeyId}; older rows are still decrypted with the key id stored next to
 * them. Keys are Base64 and come only from the environment / Secret Manager.
 */
@Validated
@ConfigurationProperties("app.crypto")
public record CryptoProperties(
        @NotBlank String activeKeyId,
        @NotEmpty Map<String, String> keys) {

    static final int AES_256_KEY_BYTES = 32;

    @AssertTrue(message = "app.crypto.active-key-id must reference one of app.crypto.keys")
    boolean isActiveKeyPresent() {
        return keys == null || activeKeyId == null || keys.containsKey(activeKeyId);
    }

    @AssertTrue(message = "every app.crypto.keys value must be Base64 and decode to exactly 32 bytes (AES-256)")
    boolean isEveryKey256Bits() {
        if (keys == null) {
            return true;
        }
        return keys.values().stream().allMatch(CryptoProperties::is256BitKey);
    }

    public byte[] keyBytes(String keyId) {
        String encoded = keys.get(keyId);
        if (encoded == null) {
            throw new IllegalStateException("Unknown encryption key id: " + keyId);
        }
        return Base64.getDecoder().decode(encoded);
    }

    private static boolean is256BitKey(String encoded) {
        try {
            return encoded != null && Base64.getDecoder().decode(encoded).length == AES_256_KEY_BYTES;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
