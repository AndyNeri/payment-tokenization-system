package com.farmatodo.challenge.notification.application;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import com.farmatodo.challenge.customer.CustomerDirectory;
import com.farmatodo.challenge.customer.CustomerDirectory.CustomerContact;
import com.farmatodo.challenge.notification.NotificationSent;
import com.farmatodo.challenge.notification.domain.Email;
import com.farmatodo.challenge.notification.domain.EmailSender;
import com.farmatodo.challenge.notification.domain.PaymentEmails;
import com.farmatodo.challenge.order.OrderEvents.PaymentFailed;
import com.farmatodo.challenge.order.OrderEvents.PaymentSucceeded;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Sends the payment result email. Runs asynchronously after the checkout commits (the customer gets the HTTP
 * response without waiting for SMTP). If SMTP fails the exception propagates, the event publication stays
 * incomplete in the outbox and {@code EventRetryConfig} resubmits it later.
 */
@Component
class PaymentNotificationListener {

    static final String PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED_EMAIL";
    static final String PAYMENT_FAILED = "PAYMENT_FAILED_EMAIL";

    private static final Logger log = LoggerFactory.getLogger(PaymentNotificationListener.class);

    private final CustomerDirectory customers;
    private final EmailSender sender;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PaymentNotificationListener(CustomerDirectory customers, EmailSender sender, ApplicationEventPublisher events,
            Clock clock) {
        this.customers = customers;
        this.sender = sender;
        this.events = events;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void on(PaymentSucceeded event) {
        contactOf(event.customerId(), event.orderId()).ifPresent(contact -> deliver(PAYMENT_SUCCEEDED,
                PaymentEmails.succeeded(contact.email(), contact.firstName(), event.orderId(), event.total(),
                        event.attempts(), event.occurredAt()),
                event.orderId(), event.customerId(), event.transactionId()));
    }

    @ApplicationModuleListener
    void on(PaymentFailed event) {
        contactOf(event.customerId(), event.orderId()).ifPresent(contact -> deliver(PAYMENT_FAILED,
                PaymentEmails.failed(contact.email(), contact.firstName(), event.orderId(), event.total(),
                        event.attempts(), event.occurredAt()),
                event.orderId(), event.customerId(), event.transactionId()));
    }

    /** A customer deleted after paying cannot be emailed: skip instead of retrying forever (poison message). */
    private Optional<CustomerContact> contactOf(UUID customerId, UUID orderId) {
        Optional<CustomerContact> contact = customers.contactOf(customerId);
        if (contact.isEmpty()) {
            log.warn("No contact for customer {}: payment email for order {} skipped", customerId, orderId);
        }
        return contact;
    }

    private void deliver(String type, Email email, UUID orderId, UUID customerId, UUID transactionId) {
        sender.send(email);
        log.info("Email {} sent to {} for order {}", type, email.maskedRecipient(), orderId);
        events.publishEvent(new NotificationSent(type, orderId, customerId, transactionId, clock.instant()));
    }
}
