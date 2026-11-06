package com.farmatodo.challenge.tokenization.domain;

import static com.farmatodo.challenge.tokenization.TestCards.AMEX;
import static com.farmatodo.challenge.tokenization.TestCards.DINERS;
import static com.farmatodo.challenge.tokenization.TestCards.DISCOVER;
import static com.farmatodo.challenge.tokenization.TestCards.MASTERCARD;
import static com.farmatodo.challenge.tokenization.TestCards.MASTERCARD_2_SERIES;
import static com.farmatodo.challenge.tokenization.TestCards.VISA;
import static com.farmatodo.challenge.tokenization.TestCards.VISA_13;
import static com.farmatodo.challenge.tokenization.TestCards.VISA_BAD_LUHN;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CardNumberTest {

    static Stream<Arguments> brands() {
        return Stream.of(
                Arguments.of(VISA, CardBrand.VISA),
                Arguments.of(VISA_13, CardBrand.VISA),
                Arguments.of(MASTERCARD, CardBrand.MASTERCARD),
                Arguments.of(MASTERCARD_2_SERIES, CardBrand.MASTERCARD),
                Arguments.of(AMEX, CardBrand.AMEX),
                Arguments.of(DISCOVER, CardBrand.DISCOVER),
                Arguments.of(DINERS, CardBrand.DINERS));
    }

    @ParameterizedTest
    @MethodSource("brands")
    void detectsBrandAndPassesLuhnForKnownTestCards(String pan, CardBrand expected) {
        CardNumber number = CardNumber.parse(pan).orElseThrow();
        assertThat(number.brand()).contains(expected);
        assertThat(number.passesLuhn()).isTrue();
        assertThat(expected.acceptsLength(number.length())).isTrue();
    }

    @Test
    void detectsLuhnFailure() {
        assertThat(CardNumber.parse(VISA_BAD_LUHN).orElseThrow().passesLuhn()).isFalse();
    }

    @Test
    void normalisesSpacesAndDashes() {
        CardNumber number = CardNumber.parse("4111 1111-1111 1111").orElseThrow();
        assertThat(number.value()).isEqualTo(VISA);
        assertThat(number).isEqualTo(CardNumber.parse(VISA).orElseThrow());
        assertThat(number.hashCode()).isEqualTo(CardNumber.parse(VISA).orElseThrow().hashCode());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"4111-abcd", "   ", "4111.1111"})
    void rejectsNonNumericInput(String raw) {
        assertThat(CardNumber.parse(raw)).isEmpty();
    }

    @Test
    void unknownBrand() {
        assertThat(CardNumber.parse("9999999999999995").orElseThrow().brand()).isEmpty();
        assertThat(CardBrand.detect("5")).isEmpty();
    }

    @Test
    void neverPrintsTheFullNumber() {
        CardNumber number = CardNumber.parse(VISA).orElseThrow();
        assertThat(number.toString()).doesNotContain(VISA).endsWith("1111");
        assertThat(number.last4()).isEqualTo("1111");
    }

    @Test
    void cvvLengthPerBrand() {
        assertThat(CardBrand.AMEX.cvvLength()).isEqualTo(4);
        assertThat(CardBrand.VISA.cvvLength()).isEqualTo(3);
    }
}
