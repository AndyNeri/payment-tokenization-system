package com.farmatodo.challenge.auth.application;

import java.time.Duration;
import java.util.UUID;

import com.farmatodo.challenge.customer.CustomerAuthenticator;
import com.farmatodo.challenge.shared.error.Exceptions.UnauthorizedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Login: validates credentials through the customer module's API and issues a short-lived JWT.
 * The error is identical for "unknown email" and "wrong password" (no user enumeration).
 */
@Service
public class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    private final CustomerAuthenticator authenticator;
    private final TokenIssuer tokenIssuer;

    LoginService(CustomerAuthenticator authenticator, TokenIssuer tokenIssuer) {
        this.authenticator = authenticator;
        this.tokenIssuer = tokenIssuer;
    }

    public AccessToken login(String email, String password) {
        UUID customerId = authenticator.authenticate(email, password).orElseThrow(() -> {
            log.info("Failed login attempt");
            return new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password");
        });
        log.info("Customer {} logged in", customerId);
        return tokenIssuer.issueFor(customerId);
    }

    /** Outbound port: creates signed access tokens. */
    public interface TokenIssuer {
        AccessToken issueFor(UUID customerId);
    }

    public record AccessToken(String value, Duration expiresIn) {
        @Override
        public String toString() {
            return "AccessToken[***]";
        }
    }
}
