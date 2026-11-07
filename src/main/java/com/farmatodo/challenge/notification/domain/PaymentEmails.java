package com.farmatodo.challenge.notification.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Content of the payment emails (Spanish, the customers' language). Pure functions: easy to unit test. */
public final class PaymentEmails {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    private PaymentEmails() {
    }

    public static Email succeeded(String to, String firstName, UUID orderId, BigDecimal total, int attempts,
            Instant when) {
        String body = """
                Hola %s,

                Tu pago fue aprobado y tu pedido está confirmado.

                Pedido: %s
                Total: $%s
                Fecha: %s
                Intentos de pago: %d

                Gracias por comprar con nosotros.
                """.formatted(firstName, orderId, money(total), DATE.format(when), attempts);
        return new Email(to, "Pago aprobado - pedido " + shortId(orderId), body);
    }

    public static Email failed(String to, String firstName, UUID orderId, BigDecimal total, int attempts,
            Instant when) {
        String body = """
                Hola %s,

                No pudimos procesar el pago de tu pedido después de %d intentos.

                Pedido: %s
                Total: $%s
                Fecha: %s

                No se realizó ningún cobro. Tus productos siguen en el carrito: puedes intentarlo de nuevo
                con la misma tarjeta o con otra.
                """.formatted(firstName, attempts, orderId, money(total), DATE.format(when));
        return new Email(to, "No pudimos procesar tu pago - pedido " + shortId(orderId), body);
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String shortId(UUID id) {
        String value = id.toString();
        return value.substring(value.length() - 8);
    }
}
