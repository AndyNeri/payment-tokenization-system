package com.farmatodo.challenge.order;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Builds the state a checkout needs (a tokenized card and a filled cart) through the real APIs. */
final class OrderFixtures {

    static final String API_KEY = "test-api-key-0123456789-abcdefghijklmnop";

    private OrderFixtures() {
    }

    static String tokenizeVisa(MockMvcTester mvc, UUID customerId) throws Exception {
        MvcTestResult result = mvc.post().uri("/api/v1/tokens").header("X-API-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerId":"%s","cardNumber":"4111111111111111","cvv":"123","expirationMonth":12,
                         "expirationYear":2030,"holderName":"Andres Neri"}""".formatted(customerId))
                .exchange();
        return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.token");
    }

    static void addToCart(MockMvcTester mvc, UUID customerId, UUID productId, int quantity) {
        mvc.post().uri("/api/v1/me/cart/items").with(jwt().jwt(j -> j.subject(customerId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"%s\",\"quantity\":%d}".formatted(productId, quantity)).exchange();
    }

    static MvcTestResult placeOrder(MockMvcTester mvc, UUID customerId, String cardToken, String address) {
        String body = address == null
                ? "{\"cardToken\":\"%s\"}".formatted(cardToken)
                : "{\"cardToken\":\"%s\",\"deliveryAddress\":\"%s\"}".formatted(cardToken, address);
        return mvc.post().uri("/api/v1/orders").with(jwt().jwt(j -> j.subject(customerId.toString())))
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    static String json(MvcTestResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), path).toString();
    }
}
