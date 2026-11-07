package com.farmatodo.challenge.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PaymentEmailsTest {

    static final UUID ORDER = UUID.fromString("019a5b2e-0000-7000-8000-0000abcd1234");
    static final Instant WHEN = Instant.parse("2026-11-07T15:30:00Z");

    @Test
    void succeededEmailHasTheOrderTotalAndAttempts() {
        Email email = PaymentEmails.succeeded("ana@mail.com", "Ana", ORDER, new BigDecimal("21"), 2, WHEN);

        assertThat(email.to()).isEqualTo("ana@mail.com");
        assertThat(email.subject()).isEqualTo("Pago aprobado - pedido abcd1234");
        assertThat(email.body()).contains("Hola Ana", ORDER.toString(), "$21.00", "2026-11-07 15:30 UTC",
                "Intentos de pago: 2");
    }

    @Test
    void failedEmailSaysNothingWasChargedAndTheCartIsKept() {
        Email email = PaymentEmails.failed("ana@mail.com", "Ana", ORDER, new BigDecimal("8.405"), 4, WHEN);

        assertThat(email.subject()).startsWith("No pudimos procesar tu pago");
        assertThat(email.body()).contains("después de 4 intentos", "$8.41", "No se realizó ningún cobro", "carrito");
    }

    @Test
    void recipientIsMaskedForLogsAndRequired() {
        assertThat(new Email("andres@gmail.com", "s", "b").maskedRecipient()).isEqualTo("a***@gmail.com");
        assertThat(new Email("a@x.com", "s", "b").maskedRecipient()).isEqualTo("***@x.com");
        assertThat(new Email("nobody", "s", "b").maskedRecipient()).isEqualTo("***nobody");
        assertThatThrownBy(() -> new Email(" ", "s", "b")).isInstanceOf(IllegalArgumentException.class);
    }
}
