package com.farmatodo.challenge.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import com.farmatodo.challenge.auth.application.LoginService.AccessToken;
import com.farmatodo.challenge.customer.CustomerAuthenticator;
import com.farmatodo.challenge.shared.error.Exceptions.UnauthorizedException;

import org.junit.jupiter.api.Test;

class LoginServiceTest {

    CustomerAuthenticator authenticator = mock(CustomerAuthenticator.class);
    UUID customerId = UUID.randomUUID();
    LoginService service = new LoginService(authenticator, id -> new AccessToken("jwt-for-" + id, Duration.ofMinutes(30)));

    @Test
    void issuesTokenForValidCredentials() {
        when(authenticator.authenticate("a@b.c", "pw")).thenReturn(Optional.of(customerId));

        AccessToken token = service.login("a@b.c", "pw");

        assertThat(token.value()).isEqualTo("jwt-for-" + customerId);
        assertThat(token.toString()).doesNotContain("jwt-for");
    }

    @Test
    void invalidCredentialsAre401WithGenericMessage() {
        when(authenticator.authenticate("a@b.c", "bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("a@b.c", "bad"))
                .isInstanceOf(UnauthorizedException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_CREDENTIALS")
                .hasMessage("Invalid email or password");
    }
}
