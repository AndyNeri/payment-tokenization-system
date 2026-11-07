package com.farmatodo.challenge.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.farmatodo.challenge.audit.domain.AuditEntry;
import com.farmatodo.challenge.audit.domain.AuditEntry.Outcome;
import com.farmatodo.challenge.audit.domain.AuditLog;
import com.farmatodo.challenge.customer.CustomerRegistered;
import com.farmatodo.challenge.notification.NotificationSent;
import com.farmatodo.challenge.order.OrderEvents.OrderPlaced;
import com.farmatodo.challenge.order.OrderEvents.PaymentAttempted;
import com.farmatodo.challenge.order.OrderEvents.PaymentFailed;
import com.farmatodo.challenge.order.OrderEvents.PaymentSucceeded;
import com.farmatodo.challenge.product.ProductsSearched;
import com.farmatodo.challenge.tokenization.CardTokenized;
import com.farmatodo.challenge.tokenization.TokenizationRejected;

import org.junit.jupiter.api.Test;

/** Every domain event becomes one audit row with its transactionId and the right outcome. */
class AuditListenerTest {

    static final Instant NOW = Instant.parse("2026-11-07T15:30:00Z");

    List<AuditEntry> stored = new ArrayList<>();
    AuditListener listener = new AuditListener(new AuditLog() {
        @Override
        public void append(AuditEntry entry) {
            stored.add(entry);
        }

        @Override
        public List<AuditEntry> findByTransactionId(UUID transactionId) {
            return stored.stream().filter(e -> e.transactionId().equals(transactionId)).toList();
        }
    });

    UUID tx = UUID.randomUUID();
    UUID customer = UUID.randomUUID();
    UUID order = UUID.randomUUID();

    @Test
    void mapsEveryEventOfAPurchase() {
        UUID card = UUID.randomUUID();
        listener.on(new CustomerRegistered(customer, tx, NOW));
        listener.on(new CardTokenized(card, customer, "VISA", "1111", tx, NOW));
        listener.on(new TokenizationRejected(customer, "VISA", "1111", "RANDOM_REJECTION", tx, NOW));
        listener.on(new ProductsSearched(null, 40, customer, tx, NOW));
        listener.on(new OrderPlaced(order, customer, new BigDecimal("21.00"), 2, tx, NOW));
        listener.on(new PaymentAttempted(order, 1, "REJECTED", tx, NOW));
        listener.on(new PaymentAttempted(order, 2, "APPROVED", tx, NOW));
        listener.on(new PaymentSucceeded(order, customer, new BigDecimal("21.00"), 2, tx, NOW));
        listener.on(new PaymentFailed(order, customer, new BigDecimal("21.00"), 4, tx, NOW));
        listener.on(new NotificationSent("PAYMENT_SUCCEEDED_EMAIL", order, customer, tx, NOW));

        assertThat(stored).extracting(AuditEntry::eventType).containsExactly("CUSTOMER_REGISTERED", "CARD_TOKENIZED",
                "TOKENIZATION_REJECTED", "PRODUCTS_SEARCHED", "ORDER_PLACED", "PAYMENT_ATTEMPTED", "PAYMENT_ATTEMPTED",
                "PAYMENT_SUCCEEDED", "PAYMENT_FAILED", "EMAIL_SENT");
        assertThat(stored).extracting(AuditEntry::outcome).containsExactly(Outcome.SUCCESS, Outcome.SUCCESS,
                Outcome.FAILURE, Outcome.SUCCESS, Outcome.SUCCESS, Outcome.FAILURE, Outcome.SUCCESS,
                Outcome.SUCCESS, Outcome.FAILURE, Outcome.SUCCESS);
        assertThat(stored).allSatisfy(e -> {
            assertThat(e.transactionId()).isEqualTo(tx);
            assertThat(e.occurredAt()).isEqualTo(NOW);
        });
    }

    @Test
    void detailsCarryOnlyNonSensitiveData() {
        UUID card = UUID.randomUUID();
        listener.on(new CardTokenized(card, customer, "VISA", "1111", tx, NOW));
        listener.on(new ProductsSearched(null, 40, customer, tx, NOW));

        AuditEntry tokenized = stored.get(0);
        assertThat(tokenized.entityType()).isEqualTo("CARD");
        assertThat(tokenized.entityId()).isEqualTo(card.toString());
        assertThat(tokenized.details()).containsEntry("brand", "VISA").containsEntry("last4", "1111")
                .containsEntry("customerId", customer.toString()).hasSize(3);
        assertThat(stored.get(1).details()).containsEntry("query", null).containsEntry("results", 40L);
    }
}
