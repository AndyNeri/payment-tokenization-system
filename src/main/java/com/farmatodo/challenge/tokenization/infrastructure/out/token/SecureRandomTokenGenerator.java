package com.farmatodo.challenge.tokenization.infrastructure.out.token;

import java.security.SecureRandom;
import java.util.Base64;

import com.farmatodo.challenge.tokenization.domain.Ports.TokenGenerator;

import org.springframework.stereotype.Component;

/**
 * Tokens are 192 random bits from a CSPRNG, URL-safe Base64 encoded, with a "tok_" prefix.
 * They have no mathematical relation to the PAN, so a leaked token reveals nothing about the card.
 * Collisions are practically impossible (2^192 space) and the UNIQUE constraint guards anyway.
 */
@Component
class SecureRandomTokenGenerator implements TokenGenerator {

    static final String PREFIX = "tok_";
    static final int RANDOM_BYTES = 24;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    @Override
    public String generate() {
        byte[] bytes = new byte[RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        return PREFIX + encoder.encodeToString(bytes);
    }
}
