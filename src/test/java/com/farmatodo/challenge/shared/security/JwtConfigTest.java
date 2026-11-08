package com.farmatodo.challenge.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import java.util.stream.Stream;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;

/** HS256 encoder/decoder wiring without Spring: issuer check, expiry check and extra validators. */
class JwtConfigTest {

    static final String SECRET = Base64.getEncoder().encodeToString("unit-test-jwt-secret-32-bytes-long!!".getBytes());
    SecurityProperties properties = new SecurityProperties("k".repeat(32),
            new SecurityProperties.Jwt(SECRET, Duration.ofMinutes(30), "payment-tokenization-system"));
    JwtConfig config = new JwtConfig();
    SecretKey key = config.jwtSecretKey(properties);
    JwtEncoder encoder = config.jwtEncoder(key);

    String token(String issuer, Instant expiresAt, String subject) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(subject)
                .issuedAt(expiresAt.minusSeconds(60)).expiresAt(expiresAt).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @SuppressWarnings("unchecked")
    JwtDecoder decoder(OAuth2TokenValidator<Jwt>... extra) {
        ObjectProvider<OAuth2TokenValidator<Jwt>> provider = mock(ObjectProvider.class);
        when(provider.orderedStream()).thenAnswer(inv -> Stream.of(extra));
        return config.jwtDecoder(key, properties, provider);
    }

    @Test
    void validTokenRoundTrips() {
        Jwt jwt = decoder().decode(token("payment-tokenization-system", Instant.now().plusSeconds(600), "c-1"));
        assertThat(jwt.getSubject()).isEqualTo("c-1");
    }

    @Test
    void wrongIssuerAndExpiredTokensAreRejected() {
        JwtDecoder decoder = decoder();
        assertThatThrownBy(() -> decoder.decode(token("someone-else", Instant.now().plusSeconds(600), "c-1")))
                .isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> decoder.decode(token("payment-tokenization-system", Instant.now().minusSeconds(600), "c-1")))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void extraValidatorBeansAreApplied() {
        OAuth2TokenValidator<Jwt> rejectAll = jwt -> OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        assertThatThrownBy(() -> decoder(rejectAll).decode(token("payment-tokenization-system",
                Instant.now().plusSeconds(600), "c-1"))).isInstanceOf(JwtValidationException.class);
    }
}
