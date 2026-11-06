package com.farmatodo.challenge.tokenization.application;

import java.util.UUID;

import com.farmatodo.challenge.tokenization.domain.Card;

/** Inbound port: tokenize a credit card for a customer. */
public interface TokenizeCardUseCase {

    TokenizationResult tokenize(TokenizeCardCommand command);

    /** Input. {@link #toString()} is masked: the PAN and CVV must never reach logs. */
    record TokenizeCardCommand(UUID customerId, String cardNumber, String cvv, int expirationMonth,
                               int expirationYear, String holderName) {
        @Override
        public String toString() {
            return "TokenizeCardCommand[customerId=" + customerId + ", cardNumber=****, cvv=***]";
        }
    }

    /**
     * A rejection by the simulated policy is a <em>business outcome</em>, not an exception: the use case
     * commits its audit event either way, and the web adapter maps {@link Rejected} to HTTP 422.
     */
    sealed interface TokenizationResult {
        record Tokenized(Card card) implements TokenizationResult {
        }

        record Rejected(String reason) implements TokenizationResult {
        }
    }
}
