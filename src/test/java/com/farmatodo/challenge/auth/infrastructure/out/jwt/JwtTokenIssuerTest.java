package com.farmatodo.challenge.auth.infrastructure.out.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import com.farmatodo.challenge.auth.application.LoginService.AccessToken;
import com.farmatodo.challenge.shared.security.SecurityProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtTokenIssuerTest {

    @Test
    void issuesAnHs256TokenForTheCustomerWithIssuerAndExpiry() {
        byte[] secret = "unit-test-jwt-secret-32-bytes-long!!".getBytes();
        SecretKeySpec key = new SecretKeySpec(secret, "HmacSHA256");
        SecurityProperties properties = new SecurityProperties("k".repeat(32), new SecurityProperties.Jwt(
                Base64.getEncoder().encodeToString(secret), Duration.ofMinutes(30), "payment-tokenization-system"));
        Instant now = Instant.now();
        JwtTokenIssuer issuer = new JwtTokenIssuer(new NimbusJwtEncoder(new ImmutableSecret<>(key)), properties,
                Clock.fixed(now, ZoneOffset.UTC));
        UUID customer = UUID.randomUUID();

        AccessToken token = issuer.issueFor(customer);

        Jwt jwt = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build().decode(token.value());
        assertThat(jwt.getSubject()).isEqualTo(customer.toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("payment-tokenization-system");
        assertThat(jwt.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(30)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        assertThat(jwt.getId()).isNotBlank();
        assertThat(token.expiresIn()).isEqualTo(Duration.ofMinutes(30));
    }
}
