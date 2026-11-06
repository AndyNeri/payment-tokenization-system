package com.farmatodo.challenge.cart.infrastructure.in.web;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import com.farmatodo.challenge.cart.application.CartService;
import com.farmatodo.challenge.cart.application.CartService.CartView;
import com.farmatodo.challenge.cart.domain.Cart;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The authenticated customer's cart. The customer comes from the JWT (no customer id in the URL). */
@RestController
@RequestMapping("/api/v1/me/cart")
class CartController {

    private final CartService cartService;

    CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    CartView view(@AuthenticationPrincipal Jwt jwt) {
        return cartService.view(customerId(jwt));
    }

    @PostMapping("/items")
    CartView add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddItemRequest request) {
        return cartService.add(customerId(jwt), request.productId(), request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    CartView remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID productId) {
        return cartService.remove(customerId(jwt), productId);
    }

    private static UUID customerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record AddItemRequest(@NotNull UUID productId,
                          @NotNull @Min(1) @Max(Cart.MAX_QUANTITY_PER_PRODUCT) Integer quantity) {
    }
}
