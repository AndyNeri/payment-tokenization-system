package com.farmatodo.challenge.tokenization.domain;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Supported card networks, detected from the leading digits (IIN/BIN ranges).
 * Each brand defines its valid PAN lengths and CVV length.
 */
public enum CardBrand {

    AMEX(Set.of(15), 4, pan -> pan.startsWith("34") || pan.startsWith("37")),
    DINERS(Set.of(14), 3, pan -> pan.startsWith("36") || pan.startsWith("38") || pan.startsWith("39")
            || between(pan, 3, 300, 305)),
    DISCOVER(Set.of(16, 17, 18, 19), 3, pan -> pan.startsWith("6011") || pan.startsWith("65")
            || between(pan, 3, 644, 649)),
    MASTERCARD(Set.of(16), 3, pan -> between(pan, 2, 51, 55) || between(pan, 4, 2221, 2720)),
    VISA(Set.of(13, 16, 19), 3, pan -> pan.startsWith("4"));

    private final Set<Integer> validLengths;
    private final int cvvLength;
    private final Predicate<String> prefixMatcher;

    CardBrand(Set<Integer> validLengths, int cvvLength, Predicate<String> prefixMatcher) {
        this.validLengths = validLengths;
        this.cvvLength = cvvLength;
        this.prefixMatcher = prefixMatcher;
    }

    public static Optional<CardBrand> detect(String digits) {
        for (CardBrand brand : values()) {
            if (brand.prefixMatcher.test(digits)) {
                return Optional.of(brand);
            }
        }
        return Optional.empty();
    }

    public boolean acceptsLength(int length) {
        return validLengths.contains(length);
    }

    public int cvvLength() {
        return cvvLength;
    }

    private static boolean between(String digits, int prefixLength, int from, int to) {
        if (digits.length() < prefixLength) {
            return false;
        }
        int prefix = Integer.parseInt(digits.substring(0, prefixLength));
        return prefix >= from && prefix <= to;
    }
}
