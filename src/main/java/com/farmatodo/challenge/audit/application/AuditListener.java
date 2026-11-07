package com.farmatodo.challenge.audit.application;

import java.util.LinkedHashMap;
import java.util.Map;

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

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Centralized audit trail: listens to the domain events of every module and stores one row per event with the
 * request's transactionId. Asynchronous and transactional (outbox): auditing never slows down or breaks the
 * business request, and is not lost if the app restarts.
 * The audit module only <em>depends</em> on the other modules' public events; nobody depends on audit.
 */
@Component
class AuditListener {

    private final AuditLog auditLog;

    AuditListener(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @ApplicationModuleListener
    void on(CustomerRegistered e) {
        record(AuditEntry.of(e.transactionId(), "CUSTOMER_REGISTERED", "CUSTOMER", e.customerId(), Outcome.SUCCESS,
                Map.of(), e.occurredAt()));
    }

    @ApplicationModuleListener
    void on(CardTokenized e) {
        record(AuditEntry.of(e.transactionId(), "CARD_TOKENIZED", "CARD", e.cardId(), Outcome.SUCCESS,
                details("customerId", e.customerId(), "brand", e.brand(), "last4", e.last4()), e.occurredAt()));
    }

    @ApplicationModuleListener
    void on(TokenizationRejected e) {
        record(AuditEntry.of(e.transactionId(), "TOKENIZATION_REJECTED", "CUSTOMER", e.customerId(), Outcome.FAILURE,
                details("brand", e.brand(), "last4", e.last4(), "reason", e.reason()), e.occurredAt()));
    }

    @ApplicationModuleListener
    void on(ProductsSearched e) {
        record(AuditEntry.of(e.transactionId(), "PRODUCTS_SEARCHED", "CUSTOMER", e.customerId(), Outcome.SUCCESS,
                details("query", e.query(), "results", e.resultsCount()), e.occurredAt()));
    }

    @ApplicationModuleListener
    void on(OrderPlaced e) {
        record(AuditEntry.of(e.transactionId(), "ORDER_PLACED", "ORDER", e.orderId(), Outcome.SUCCESS,
                details("customerId", e.customerId(), "total", e.total(), "items", e.items()), e.occurredAt()));
    }

    @ApplicationModuleListener
    void on(PaymentAttempted e) {
        Outcome outcome = "APPROVED".equals(e.result()) ? Outcome.SUCCESS : Outcome.FAILURE;
        record(AuditEntry.of(e.transactionId(), "PAYMENT_ATTEMPTED", "ORDER", e.orderId(), outcome,
                details("attempt", e.attempt(), "result", e.result()), e.occurredAt()));
    }

    @ApplicationModuleListener
    void on(PaymentSucceeded e) {
        record(AuditEntry.of(e.transactionId(), "PAYMENT_SUCCEEDED", "ORDER", e.orderId(), Outcome.SUCCESS,
                details("total", e.total(), "attempts", e.attempts()), e.occurredAt()));
    }

    @ApplicationModuleListener
    void on(PaymentFailed e) {
        record(AuditEntry.of(e.transactionId(), "PAYMENT_FAILED", "ORDER", e.orderId(), Outcome.FAILURE,
                details("total", e.total(), "attempts", e.attempts()), e.occurredAt()));
    }

    @ApplicationModuleListener
    void on(NotificationSent e) {
        record(AuditEntry.of(e.transactionId(), "EMAIL_SENT", "ORDER", e.orderId(), Outcome.SUCCESS,
                details("type", e.type(), "customerId", e.customerId()), e.occurredAt()));
    }

    private void record(AuditEntry entry) {
        auditLog.append(entry);
    }

    /** Ordered map that tolerates null values (e.g. a "list all" search has no query). */
    private static Map<String, Object> details(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            Object value = keyValues[i + 1];
            map.put((String) keyValues[i], value instanceof java.util.UUID ? value.toString() : value);
        }
        return map;
    }
}
