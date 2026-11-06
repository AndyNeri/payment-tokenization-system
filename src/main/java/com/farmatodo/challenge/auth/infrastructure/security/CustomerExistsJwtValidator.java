package com.farmatodo.challenge.auth.infrastructure.security;

import java.util.UUID;

import com.farmatodo.challenge.customer.CustomerDirectory;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * A JWT is self-contained: signature and expiry say nothing about whether its subject still exists.
 * This validator closes that gap: tokens whose customer was deleted (or whose database was reset) are rejected
 * with 401 at the edge, instead of failing deep inside the application (e.g. FK errors in async listeners).
 * It also acts as immediate revocation: removing a customer invalidates their tokens before they expire.
 * <p>
 * Cost: one primary-key lookup per authenticated request. It lives in the auth module and is picked up by the
 * shared JwtDecoder as a generic {@link OAuth2TokenValidator}, so shared never depends on customer (no cycle).
 */
@Component
class CustomerExistsJwtValidator implements OAuth2TokenValidator<Jwt> {

    static final OAuth2Error ACCOUNT_NOT_FOUND = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
            "The account for this token no longer exists", null);

    private final CustomerDirectory customers;

    CustomerExistsJwtValidator(CustomerDirectory customers) {
        this.customers = customers;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            UUID customerId = UUID.fromString(jwt.getSubject());
            return customers.exists(customerId)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(ACCOUNT_NOT_FOUND);
        } catch (IllegalArgumentException | NullPointerException e) {
            return OAuth2TokenValidatorResult.failure(ACCOUNT_NOT_FOUND);
        }
    }
}
