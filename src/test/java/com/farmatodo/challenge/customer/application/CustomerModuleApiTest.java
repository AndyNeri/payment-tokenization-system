package com.farmatodo.challenge.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.farmatodo.challenge.customer.domain.Customer;
import com.farmatodo.challenge.customer.domain.CustomerPorts.CustomerRepository;
import com.farmatodo.challenge.customer.domain.CustomerPorts.PasswordHasher;

import org.junit.jupiter.api.Test;

class CustomerModuleApiTest {

    static final UUID ID = UUID.randomUUID();
    static final Customer CUSTOMER = new Customer(ID, "A", "B", "andres@mail.com", "+584121234567", "Addr",
            "real-hash", Instant.EPOCH);

    CustomerRepository repository = mock(CustomerRepository.class);
    PasswordHasher hasher = mock(PasswordHasher.class);
    CustomerModuleApi api;

    CustomerModuleApiTest() {
        when(hasher.hash("timing-equalizer-not-a-real-password")).thenReturn("dummy-hash");
        api = new CustomerModuleApi(repository, hasher);
    }

    @Test
    void authenticatesWithNormalisedEmailAndCorrectPassword() {
        when(repository.findByEmail("andres@mail.com")).thenReturn(Optional.of(CUSTOMER));
        when(hasher.matches("S3gura!2026", "real-hash")).thenReturn(true);

        assertThat(api.authenticate(" Andres@Mail.com ", "S3gura!2026")).contains(ID);
    }

    @Test
    void wrongPasswordIsEmpty() {
        when(repository.findByEmail("andres@mail.com")).thenReturn(Optional.of(CUSTOMER));
        when(hasher.matches("wrong", "real-hash")).thenReturn(false);

        assertThat(api.authenticate("andres@mail.com", "wrong")).isEmpty();
    }

    @Test
    void unknownEmailStillRunsAHashComparison() {
        when(repository.findByEmail("ghost@mail.com")).thenReturn(Optional.empty());

        assertThat(api.authenticate("ghost@mail.com", "whatever")).isEmpty();
        verify(hasher).matches("whatever", "dummy-hash");   // same cost as a real check: no user enumeration
    }

    @Test
    void nullCredentialsAreEmpty() {
        assertThat(api.authenticate(null, "x")).isEmpty();
        assertThat(api.authenticate("a@b.c", null)).isEmpty();
    }

    @Test
    void existsDelegatesToRepository() {
        when(repository.existsById(ID)).thenReturn(true);
        assertThat(api.exists(ID)).isTrue();
        assertThat(api.exists(UUID.randomUUID())).isFalse();
    }
}
