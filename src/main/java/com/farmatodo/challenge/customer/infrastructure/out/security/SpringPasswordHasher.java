package com.farmatodo.challenge.customer.infrastructure.out.security;

import com.farmatodo.challenge.customer.domain.CustomerPorts.PasswordHasher;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Adapter over Spring Security's delegating encoder: stores "{bcrypt}$2a$10$...", salted and slow by design. */
@Component
class SpringPasswordHasher implements PasswordHasher {

    private final PasswordEncoder encoder;

    SpringPasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hash) {
        return encoder.matches(rawPassword, hash);
    }
}
