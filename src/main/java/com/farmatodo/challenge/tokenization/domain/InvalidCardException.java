package com.farmatodo.challenge.tokenization.domain;

import java.util.List;

import com.farmatodo.challenge.shared.error.Exceptions.InvalidInputException;

/** Card data failed business validation (Luhn, brand, length, CVV, expiration). → 400 INVALID_CARD */
public class InvalidCardException extends InvalidInputException {

    public InvalidCardException(List<FieldError> errors) {
        super("INVALID_CARD", "The card data failed validation", errors);
    }
}
