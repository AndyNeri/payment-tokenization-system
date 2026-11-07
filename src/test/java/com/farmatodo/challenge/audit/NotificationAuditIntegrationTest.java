package com.farmatodo.challenge.audit;

import static com.farmatodo.challenge.order.OrderFixtures.API_KEY;
import static com.farmatodo.challenge.order.OrderFixtures.addToCart;
import static com.farmatodo.challenge.order.OrderFixtures.json;
import static com.farmatodo.challenge.order.OrderFixtures.placeOrder;
import static com.farmatodo.challenge.order.OrderFixtures.tokenizeVisa;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import com.farmatodo.challenge.IntegrationTest;
import com.farmatodo.challenge.TestData;
import com.farmatodo.challenge.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * A paid order, end to end: the payment email really travels over SMTP (Mailpit container) and every step is in
 * the audit trail under the checkout's transactionId, queryable with the API key.
 */
@IntegrationTest
class NotificationAuditIntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Value("${" + TestcontainersConfiguration.MAILPIT_API_PROPERTY + "}")
    String mailpitApi;

    @Test
    void paidOrderSendsTheEmailAndLeavesTheWholeStoryInTheAuditTrail() throws Exception {
        UUID customer = TestData.insertCustomer(jdbc);
        UUID product = TestData.insertProduct(jdbc, "Producto auditado", new BigDecimal("7.50"), 5);
        String card = tokenizeVisa(mvc, customer);
        addToCart(mvc, customer, product, 2);

        MvcTestResult checkout = placeOrder(mvc, customer, card, null);
        assertThat(checkout).hasStatus(HttpStatus.CREATED);
        String orderId = json(checkout, "$.id");
        String transactionId = checkout.getResponse().getHeader("X-Transaction-Id");

        assertThat(TestData.awaitCount(jdbc, 1, "select count(*) from audit_events where transaction_id = ?::uuid "
                + "and event_type = 'EMAIL_SENT'", transactionId)).isEqualTo(1);

        MvcTestResult audit = mvc.get().uri("/api/v1/audit").param("transactionId", transactionId)
                .header("X-API-Key", API_KEY).exchange();
        assertThat(audit).hasStatusOk();
        assertThat(audit).bodyJson().extractingPath("$[*].eventType").asArray().containsExactlyInAnyOrder(
                "ORDER_PLACED", "PAYMENT_ATTEMPTED", "PAYMENT_SUCCEEDED", "EMAIL_SENT");
        assertThat(audit).bodyJson().extractingPath("$[*].entityId").asArray().containsOnly(orderId);
        assertThat(audit).bodyJson().extractingPath("$[?(@.eventType == 'ORDER_PLACED')].details.total")
                .asArray().containsExactly(15.0);

        String recipient = "user-" + customer + "@test.dev";
        List<String> subjects = JsonPath.read(mailpit("/api/v1/search?query="
                + URLEncoder.encode("to:" + recipient, StandardCharsets.UTF_8)), "$.messages[*].Subject");
        assertThat(subjects).singleElement().asString().startsWith("Pago aprobado - pedido");
    }

    @Test
    void auditQueryNeedsTheApiKeyAndAValidTransactionId() {
        assertThat(mvc.get().uri("/api/v1/audit").param("transactionId", UUID.randomUUID().toString()))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/v1/audit").param("transactionId", UUID.randomUUID().toString())
                .header("X-API-Key", API_KEY))
                .hasStatusOk().bodyJson().isEqualTo("[]");
        assertThat(mvc.get().uri("/api/v1/audit").header("X-API-Key", API_KEY))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/api/v1/audit").param("transactionId", "not-a-uuid").header("X-API-Key", API_KEY))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    private String mailpit(String path) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create(mailpitApi + path)).build(),
                    HttpResponse.BodyHandlers.ofString()).body();
        }
    }
}
