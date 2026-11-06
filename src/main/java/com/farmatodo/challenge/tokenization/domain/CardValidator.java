package com.farmatodo.challenge.tokenization.domain;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.farmatodo.challenge.shared.error.DomainException.FieldError;

/**
 * Business validation of card data, run after syntactic Bean Validation.
 * Collects <em>all</em> problems at once so the client can fix everything in one round-trip.
 */
public final class CardValidator {

    private CardValidator() {
    }

    public static ValidatedCard validate(String rawNumber, String cvv, int expirationMonth, int expirationYear,
            String holderName, YearMonth currentMonth) {
        List<FieldError> errors = new ArrayList<>();

        Optional<CardNumber> number = CardNumber.parse(rawNumber);
        Optional<CardBrand> brand = number.flatMap(CardNumber::brand);
        if (number.isEmpty()) {
            errors.add(new FieldError("cardNumber", "must contain only digits"));
        } else {
            if (brand.isEmpty()) {
                errors.add(new FieldError("cardNumber", "card brand not supported"));
            } else if (!brand.get().acceptsLength(number.get().length())) {
                errors.add(new FieldError("cardNumber", "invalid length for " + brand.get()));
            }
            if (!number.get().passesLuhn()) {
                errors.add(new FieldError("cardNumber", "fails Luhn check"));
            }
        }

        if (cvv == null || !cvv.chars().allMatch(Character::isDigit)) {
            errors.add(new FieldError("cvv", "must contain only digits"));
        } else if (brand.isPresent() && cvv.length() != brand.get().cvvLength()) {
            errors.add(new FieldError("cvv", "must have " + brand.get().cvvLength() + " digits for " + brand.get()));
        }

        if (expirationMonth < 1 || expirationMonth > 12) {
            errors.add(new FieldError("expirationMonth", "must be between 1 and 12"));
        } else if (YearMonth.of(expirationYear, expirationMonth).isBefore(currentMonth)) {
            // A card is valid through the last day of its expiration month
            errors.add(new FieldError("expirationYear", "card is expired"));
        }

        if (holderName == null || holderName.isBlank()) {
            errors.add(new FieldError("holderName", "must not be blank"));
        }

        if (!errors.isEmpty()) {
            throw new InvalidCardException(errors);
        }
        CardData data = new CardData(number.get().value(), expirationMonth, expirationYear, holderName.strip());
        return new ValidatedCard(data, brand.get(), number.get().last4());
    }

    /** Card data that passed every check. Note there is no CVV here: it stops at validation. */
    public record ValidatedCard(CardData data, CardBrand brand, String last4) {
    }
}
