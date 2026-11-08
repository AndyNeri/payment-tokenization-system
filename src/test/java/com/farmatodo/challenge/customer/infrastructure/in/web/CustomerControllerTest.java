package com.farmatodo.challenge.customer.infrastructure.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import com.farmatodo.challenge.StandaloneMvc;
import com.farmatodo.challenge.customer.application.CustomerService;
import com.farmatodo.challenge.customer.domain.Customer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

class CustomerControllerTest {

    CustomerService service = mock(CustomerService.class);
    MockMvcTester mvc = StandaloneMvc.of(new CustomerController(service));
    UUID id = UUID.randomUUID();
    Customer ana = new Customer(id, "Ana", "Pérez", "ana@mail.com", "+584121234567", "Calle 1, Caracas",
            "{bcrypt}hash", Instant.parse("2026-11-08T10:00:00Z"));

    @AfterEach
    void logout() {
        StandaloneMvc.logout();
    }

    @Test
    void registerIs201AndNeverReturnsThePasswordHash() throws Exception {
        when(service.register(any())).thenReturn(ana);

        var result = mvc.post().uri("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName":"Ana","lastName":"Pérez","email":"ana@mail.com","phone":"+584121234567",
                 "address":"Calle 1, Caracas","password":"Secreta123"}""").exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(id.toString());
        assertThat(result.getResponse().getContentAsString()).doesNotContain("bcrypt").doesNotContain("password");
    }

    @Test
    void weakPasswordAndBadEmailAre400() {
        assertThat(mvc.post().uri("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName":"Ana","lastName":"Pérez","email":"no-es-email","phone":"+584121234567",
                 "address":"Calle 1, Caracas","password":"debil"}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[*].field").asArray().contains("email", "password");
        verifyNoInteractions(service);
    }

    @Test
    void meReturnsTheProfileOfTheTokenOwner() {
        StandaloneMvc.loggedInAs(id);
        when(service.get(id)).thenReturn(ana);

        assertThat(mvc.get().uri("/api/v1/me")).hasStatusOk().bodyJson().extractingPath("$.email").isEqualTo("ana@mail.com");
    }

    @Test
    void requestToStringHidesThePassword() {
        var request = new CustomerController.RegisterCustomerRequest("Ana", "Pérez", "ana@mail.com", "+58412",
                "Calle 1", "Secreta123");
        assertThat(request.toString()).doesNotContain("Secreta123");
    }
}
