package com.farmatodo.challenge.tokenization.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Primary Account Number (PAN) value object. Normalises input (spaces/dashes removed) and offers the
 * Luhn check, brand detection and safe representations. {@link #toString()} never reveals the full number.
 */
public final class CardNumber {

    private final String digits;

    private CardNumber(String digits) {
        this.digits = digits;
    }

    /** @return the normalised number, or empty if the input contains anything but digits, spaces or dashes. */
    public static Optional<CardNumber> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalised = raw.replace(" ", "").replace("-", "");
        if (normalised.isEmpty() || !normalised.chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        return Optional.of(new CardNumber(normalised));
    }

    /** Luhn (mod 10) checksum: catches typos and most randomly invented numbers. */
    public boolean passesLuhn() {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            if (doubleIt) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    public Optional<CardBrand> brand() {
        return CardBrand.detect(digits);
    }

    public int length() {
        return digits.length();
    }

    public String last4() {
        return digits.substring(Math.max(0, digits.length() - 4));
    }

    /** Full number. Only for the encryptor; never log or return it. */
    public String value() {
        return digits;
    }

    @Override
    public String toString() {
        return "**** **** **** " + last4();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CardNumber other && digits.equals(other.digits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(digits);
    }
}
