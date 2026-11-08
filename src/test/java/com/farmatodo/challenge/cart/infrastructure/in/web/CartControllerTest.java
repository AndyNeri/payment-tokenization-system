package com.farmatodo.challenge.cart.infrastructure.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.farmatodo.challenge.StandaloneMvc;
import com.farmatodo.challenge.cart.application.CartService;
import com.farmatodo.challenge.cart.application.CartService.CartView;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

class CartControllerTest {

    CartService service = mock(CartService.class);
    MockMvcTester mvc = StandaloneMvc.of(new CartController(service));
    UUID customer = UUID.randomUUID();
    UUID product = UUID.randomUUID();
    CartView view = new CartView(List.of(new CartView.Line(product, "Ibuprofeno", new BigDecimal("4.20"), 2,
            new BigDecimal("8.40"))), new BigDecimal("8.40"));

    @BeforeEach
    void login() {
        StandaloneMvc.loggedInAs(customer);
    }

    @AfterEach
    void logout() {
        StandaloneMvc.logout();
    }

    @Test
    void viewAddAndRemoveUseTheCustomerFromTheToken() {
        when(service.view(customer)).thenReturn(view);
        when(service.add(customer, product, 2)).thenReturn(view);
        when(service.remove(customer, product)).thenReturn(new CartView(List.of(), BigDecimal.ZERO));

        assertThat(mvc.get().uri("/api/v1/me/cart")).hasStatusOk().bodyJson().extractingPath("$.total").isEqualTo(8.4);
        assertThat(mvc.post().uri("/api/v1/me/cart/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"%s\",\"quantity\":2}".formatted(product)))
                .hasStatusOk().bodyJson().extractingPath("$.items[0].quantity").isEqualTo(2);
        assertThat(mvc.delete().uri("/api/v1/me/cart/items/{id}", product))
                .hasStatusOk().bodyJson().extractingPath("$.items").asArray().isEmpty();
    }

    @Test
    void quantityOutOfRangeIs400() {
        assertThat(mvc.post().uri("/api/v1/me/cart/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"%s\",\"quantity\":0}".formatted(product)))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_ERROR");
        verifyNoInteractions(service);
    }
}
