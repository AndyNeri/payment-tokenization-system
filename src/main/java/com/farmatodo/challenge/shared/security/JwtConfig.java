package com.farmatodo.challenge.shared.security;

import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * JWT signing (login) and verification (protected endpoints) with HS256 and a shared 256-bit secret.
 * HS256 is enough because the same service issues and validates tokens; RS256 would make sense if other
 * services had to validate them without sharing the secret (ADR-006).
 */
@Configuration(proxyBeanMethods = false)
class JwtConfig {

    @Bean
    SecretKey jwtSecretKey(SecurityProperties properties) {
        return new SecretKeySpec(properties.jwt().secretBytes(), "HmacSHA256");
    }

    /**
     * Validators: expiration/not-before (with clock skew) + issuer, plus any extra {@code OAuth2TokenValidator<Jwt>}
     * beans contributed by other modules (e.g. auth checks that the customer still exists). Collecting them by
     * type keeps shared free of dependencies on business modules.
     */
    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey, SecurityProperties properties,
            ObjectProvider<OAuth2TokenValidator<Jwt>> extraValidators) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
        extraValidators.orderedStream().forEach(validators::add);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }
}
