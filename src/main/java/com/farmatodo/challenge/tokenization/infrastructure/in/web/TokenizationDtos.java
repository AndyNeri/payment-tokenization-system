package com.farmatodo.challenge.tokenization.infrastructure.in.web;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.farmatodo.challenge.tokenization.domain.Card;

/** Request/response contracts of the tokenization API (see docs/api.md). */
final class TokenizationDtos {

    private TokenizationDtos() {
    }

    /**
     * Syntactic validation only (presence, format, ranges). Business checks (Luhn, brand, CVV length per brand,
     * expiration) run in the domain. {@link #toString()} is masked.
     */
    record TokenizeCardRequest(
            @NotNull UUID customerId,
            @NotBlank @Size(max = 23) @Pattern(regexp = "[0-9 -]+", message = "must contain only digits, spaces or dashes")
            String cardNumber,
            @NotBlank @Pattern(regexp = "\\d{3,4}", message = "must be 3 or 4 digits") String cvv,
            @NotNull @Min(1) @Max(12) Integer expirationMonth,
            @NotNull @Min(2000) @Max(2100) Integer expirationYear,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[\\p{L} .'-]+", message = "must contain only letters")
            String holderName) {

        @Override
        public String toString() {
            return "TokenizeCardRequest[customerId=" + customerId + ", cardNumber=****, cvv=***]";
        }
    }

    record CardResponse(String token, String brand, String last4, Instant createdAt) {

        static CardResponse from(Card card) {
            return new CardResponse(card.token(), card.brand().name(), card.last4(), card.createdAt());
        }
    }
}
