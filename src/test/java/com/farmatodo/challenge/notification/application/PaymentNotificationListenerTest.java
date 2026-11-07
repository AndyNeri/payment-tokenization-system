package com.farmatodo.challenge.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.farmatodo.challenge.customer.CustomerDirectory;
import com.farmatodo.challenge.customer.CustomerDirectory.CustomerContact;
import com.farmatodo.challenge.notification.NotificationSent;
import com.farmatodo.challenge.notification.domain.Email;
import com.farmatodo.challenge.order.OrderEvents.PaymentFailed;
import com.farmatodo.challenge.order.OrderEvents.PaymentSucceeded;

import org.junit.jupiter.api.Test;

/** The listener without Spring: fake directory, a recording sender and a list as event publisher. */
class PaymentNotificationListenerTest {

    static final Instant NOW = Instant.parse("2026-11-07T15:30:00Z");

    UUID customerId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    UUID transactionId = UUID.randomUUID();
    List<Email> sent = new ArrayList<>();
    List<Object> published = new ArrayList<>();

    CustomerDirectory directory(Optional<CustomerContact> contact) {
        return new CustomerDirectory() {
            @Override
            public boolean exists(UUID id) {
                return contact.isPresent();
            }

            @Override
            public Optional<CustomerContact> contactOf(UUID id) {
                return contact;
            }
        };
    }

    PaymentNotificationListener listener(Optional<CustomerContact> contact) {
        return new PaymentNotificationListener(directory(contact), sent::add, published::add,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    Optional<CustomerContact> ana() {
        return Optional.of(new CustomerContact(customerId, "Ana", "Pérez", "ana@mail.com", "Calle 1"));
    }

    @Test
    void paymentSucceededSendsTheConfirmationAndPublishesNotificationSent() {
        listener(ana()).on(new PaymentSucceeded(orderId, customerId, new BigDecimal("21.00"), 1, transactionId, NOW));

        assertThat(sent).singleElement().satisfies(email -> {
            assertThat(email.to()).isEqualTo("ana@mail.com");
            assertThat(email.subject()).startsWith("Pago aprobado");
        });
        assertThat(published).containsExactly(new NotificationSent(PaymentNotificationListener.PAYMENT_SUCCEEDED,
                orderId, customerId, transactionId, NOW));
    }

    @Test
    void paymentFailedSendsTheFailureEmail() {
        listener(ana()).on(new PaymentFailed(orderId, customerId, new BigDecimal("21.00"), 4, transactionId, NOW));

        assertThat(sent).singleElement().extracting(Email::subject).asString().startsWith("No pudimos procesar");
        assertThat(published).singleElement().extracting(e -> ((NotificationSent) e).type())
                .isEqualTo(PaymentNotificationListener.PAYMENT_FAILED);
    }

    @Test
    void unknownCustomerIsSkippedInsteadOfRetriedForever() {
        listener(Optional.empty()).on(new PaymentSucceeded(orderId, customerId, BigDecimal.ONE, 1, transactionId, NOW));

        assertThat(sent).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void smtpFailurePropagatesSoTheEventIsRetriedAndNothingIsAudited() {
        PaymentNotificationListener failing = new PaymentNotificationListener(directory(ana()), email -> {
            throw new IllegalStateException("SMTP down");
        }, published::add, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> failing.on(new PaymentSucceeded(orderId, customerId, BigDecimal.ONE, 1,
                transactionId, NOW))).hasMessage("SMTP down");
        assertThat(published).isEmpty();
    }
}
