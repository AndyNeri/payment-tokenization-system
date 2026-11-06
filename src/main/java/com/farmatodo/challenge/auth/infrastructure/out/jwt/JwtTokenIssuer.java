package com.farmatodo.challenge.auth.infrastructure.out.jwt;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.farmatodo.challenge.auth.application.LoginService.AccessToken;
import com.farmatodo.challenge.auth.application.LoginService.TokenIssuer;
import com.farmatodo.challenge.shared.security.SecurityProperties;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Issues HS256 JWTs: {@code sub} = customer id, plus {@code iss}, {@code iat}, {@code exp} and a unique {@code jti}.
 * No personal data in the token: its payload is only Base64, readable by anyone who holds it.
 */
@Component
class JwtTokenIssuer implements TokenIssuer {

    private final JwtEncoder encoder;
    private final SecurityProperties properties;
    private final Clock clock;

    JwtTokenIssuer(JwtEncoder encoder, SecurityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public AccessToken issueFor(UUID customerId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(customerId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.jwt().ttl()))
                .id(UUID.randomUUID().toString())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new AccessToken(token, properties.jwt().ttl());
    }
}
