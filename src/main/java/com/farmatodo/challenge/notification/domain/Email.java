package com.farmatodo.challenge.notification.domain;

import java.util.Objects;

/** A plain-text email ready to be sent. The sender address is configuration, not part of the message. */
public record Email(String to, String subject, String body) {

    public Email {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(body, "body");
        if (to.isBlank()) {
            throw new IllegalArgumentException("recipient must not be blank");
        }
    }

    /** Masked recipient for logs: "a***@gmail.com". Personal data never goes to logs in clear. */
    public String maskedRecipient() {
        int at = to.indexOf('@');
        return at <= 1 ? "***" + to.substring(Math.max(at, 0)) : to.charAt(0) + "***" + to.substring(at);
    }
}
