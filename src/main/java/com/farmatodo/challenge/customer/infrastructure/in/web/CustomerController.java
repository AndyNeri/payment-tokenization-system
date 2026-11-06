package com.farmatodo.challenge.customer.infrastructure.in.web;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.farmatodo.challenge.customer.application.CustomerService;
import com.farmatodo.challenge.customer.application.CustomerService.RegisterCustomerCommand;
import com.farmatodo.challenge.customer.domain.Customer;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CustomerController {

    private final CustomerService customers;

    CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    /** Public: self-registration. */
    @PostMapping("/api/v1/customers")
    ResponseEntity<CustomerResponse> register(@Valid @RequestBody RegisterCustomerRequest request) {
        Customer customer = customers.register(new RegisterCustomerCommand(request.firstName(), request.lastName(),
                request.email(), request.phone(), request.address(), request.password()));
        return ResponseEntity.created(URI.create("/api/v1/me")).body(CustomerResponse.from(customer));
    }

    /** The authenticated customer's profile (id from the JWT). */
    @GetMapping("/api/v1/me")
    CustomerResponse me(@AuthenticationPrincipal Jwt jwt) {
        return CustomerResponse.from(customers.get(UUID.fromString(jwt.getSubject())));
    }

    /**
     * Password rules: 8-64 chars with lower case, upper case and a digit. The 64 cap keeps the password under
     * BCrypt's 72-byte input limit even with multi-byte characters.
     */
    record RegisterCustomerRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "[+0-9 ().-]{8,25}", message = "must be a phone number, e.g. +584121234567")
            String phone,
            @NotBlank @Size(min = 5, max = 255) String address,
            @NotBlank @Size(min = 8, max = 64)
            @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
                    message = "must contain lower case, upper case and a digit")
            String password) {

        @Override
        public String toString() {
            return "RegisterCustomerRequest[email=" + email + ", password=***]";
        }
    }

    record CustomerResponse(UUID id, String firstName, String lastName, String email, String phone, String address,
                            Instant createdAt) {
        static CustomerResponse from(Customer c) {
            return new CustomerResponse(c.id(), c.firstName(), c.lastName(), c.email(), c.phone(), c.address(),
                    c.createdAt());
        }
    }
}
