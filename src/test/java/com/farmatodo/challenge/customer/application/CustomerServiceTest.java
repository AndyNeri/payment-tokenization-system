package com.farmatodo.challenge.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.farmatodo.challenge.customer.CustomerRegistered;
import com.farmatodo.challenge.customer.application.CustomerService.RegisterCustomerCommand;
import com.farmatodo.challenge.customer.domain.Customer;
import com.farmatodo.challenge.customer.domain.CustomerPorts.CustomerRepository;
import com.farmatodo.challenge.customer.domain.CustomerPorts.DuplicateCustomerException;
import com.farmatodo.challenge.customer.domain.CustomerPorts.PasswordHasher;
import com.farmatodo.challenge.shared.error.Exceptions.NotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class CustomerServiceTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T15:00:00Z"), ZoneOffset.UTC);

    CustomerRepository repository = mock(CustomerRepository.class);
    PasswordHasher hasher = mock(PasswordHasher.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    CustomerService service = new CustomerService(repository, hasher, events, CLOCK);

    RegisterCustomerCommand command = new RegisterCustomerCommand(" Andrés ", "Neri", "Andres@Mail.com",
            "+58 412-123.45.67", "Av. Principal 123", "S3gura!2026");

    @BeforeEach
    void setUp() {
        when(hasher.hash(anyString())).thenReturn("{bcrypt}hashed");
        when(repository.save(any())).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            return new Customer(UUID.randomUUID(), c.firstName(), c.lastName(), c.email(), c.phone(), c.address(),
                    c.passwordHash(), c.createdAt());
        });
    }

    @Test
    void registersNormalisedCustomerWithHashedPasswordAndPublishesEvent() {
        Customer saved = service.register(command);

        assertThat(saved.email()).isEqualTo("andres@mail.com");
        assertThat(saved.phone()).isEqualTo("+584121234567");
        assertThat(saved.firstName()).isEqualTo("Andrés");
        assertThat(saved.passwordHash()).isEqualTo("{bcrypt}hashed").doesNotContain("S3gura");
        assertThat(saved.createdAt()).isEqualTo(CLOCK.instant());
        assertThat(saved.toString()).doesNotContain("bcrypt");

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue()).isInstanceOfSatisfying(CustomerRegistered.class,
                e -> assertThat(e.customerId()).isEqualTo(saved.id()));
    }

    @Test
    void duplicateEmailIs409() {
        when(repository.existsByEmail("andres@mail.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(command))
                .isInstanceOf(DuplicateCustomerException.class)
                .hasFieldOrPropertyWithValue("code", "EMAIL_ALREADY_EXISTS");
        verify(repository, never()).save(any());
    }

    @Test
    void duplicatePhoneIs409() {
        when(repository.existsByPhone("+584121234567")).thenReturn(true);

        assertThatThrownBy(() -> service.register(command))
                .hasFieldOrPropertyWithValue("code", "PHONE_ALREADY_EXISTS");
    }

    @Test
    void getUnknownCustomerIs404() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "CUSTOMER_NOT_FOUND");
    }

    @Test
    void commandToStringHidesPassword() {
        assertThat(command.toString()).doesNotContain("S3gura");
    }
}
