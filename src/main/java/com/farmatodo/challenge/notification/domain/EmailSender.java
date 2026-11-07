package com.farmatodo.challenge.notification.domain;

/**
 * Outbound port: delivers an email. Today an SMTP adapter (Mailpit locally, Brevo in GCP); a WhatsApp or push
 * channel would be another adapter of a sibling port, without touching the listener.
 * Implementations throw on failure so the event stays incomplete and is retried.
 */
public interface EmailSender {
    void send(Email email);
}
