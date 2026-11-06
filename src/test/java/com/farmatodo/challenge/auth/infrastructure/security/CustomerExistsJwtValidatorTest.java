package com.farmatodo.challenge.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import com.farmatodo.challenge.customer.CustomerDirectory;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class CustomerExistsJwtValidatorTest {

    CustomerDirectory directory = mock(CustomerDirectory.class);
    CustomerExistsJwtValidator validator = new CustomerExistsJwtValidator(directory);

    static Jwt jwt(String subject) {
        return Jwt.withTokenValue("t").header("alg", "HS256").subject(subject)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    @Test
    void existingCustomerIsValid() {
        UUID id = UUID.randomUUID();
        when(directory.exists(id)).thenReturn(true);
        assertThat(validator.validate(jwt(id.toString())).hasErrors()).isFalse();
    }

    @Test
    void missingCustomerIsInvalidToken() {
        var result = validator.validate(jwt(UUID.randomUUID().toString()));
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).containsExactly(CustomerExistsJwtValidator.ACCOUNT_NOT_FOUND);
    }

    @Test
    void nonUuidOrMissingSubjectIsInvalidToken() {
        assertThat(validator.validate(jwt("not-a-uuid")).hasErrors()).isTrue();
        Jwt noSubject = Jwt.withTokenValue("t").header("alg", "HS256").claim("x", "y").build();
        assertThat(validator.validate(noSubject).hasErrors()).isTrue();
    }
}
