package com.farmatodo.challenge.shared.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Availability check required by the challenge: "/ping" returns "pong" with HTTP 200, for every API.
 * Besides the global ping, each slice exposes /api/v1/{slice}/ping (public, no auth).
 */
@RestController
class PingController {

    @GetMapping(value = {
            "/ping",
            "/api/v1/tokens/ping",
            "/api/v1/customers/ping",
            "/api/v1/auth/ping",
            "/api/v1/products/ping",
            "/api/v1/cart/ping",
            "/api/v1/orders/ping",
            "/api/v1/audit/ping"
    }, produces = MediaType.TEXT_PLAIN_VALUE)
    String ping() {
        return "pong";
    }
}
