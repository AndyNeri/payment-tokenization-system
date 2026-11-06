package com.farmatodo.challenge.tokenization.domain;

import static com.farmatodo.challenge.tokenization.TestCards.AMEX;
import static com.farmatodo.challenge.tokenization.TestCards.VISA;
import static com.farmatodo.challenge.tokenization.TestCards.VISA_BAD_LUHN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;

import com.farmatodo.challenge.shared.error.DomainException.FieldError;
import com.farmatodo.challenge.tokenization.domain.CardValidator.ValidatedCard;

import org.junit.jupiter.api.Test;

class CardValidatorTest {

    static final YearMonth NOW = YearMonth.of(2026, 11);

    @Test
    void acceptsValidCardAndKeepsOnlyWhatIsNeeded() {
        ValidatedCard card = CardValidator.validate("4111 1111 1111 1111", "123", 12, 2029, "  Andrés Neri ", NOW);

        assertThat(card.brand()).isEqualTo(CardBrand.VISA);
        assertThat(card.last4()).isEqualTo("1111");
        assertThat(card.data()).isEqualTo(new CardData(VISA, 12, 2029, "Andrés Neri"));
    }

    @Test
    void cardIsValidThroughItsExpirationMonth() {
        assertThat(CardValidator.validate(VISA, "123", 11, 2026, "A B", NOW)).isNotNull();
    }

    @Test
    void rejectsExpiredCard() {
        assertThatThrownBy(() -> CardValidator.validate(VISA, "123", 10, 2026, "A B", NOW))
                .isInstanceOf(InvalidCardException.class)
                .satisfies(e -> assertThat(((InvalidCardException) e).fieldErrors())
                        .containsExactly(new FieldError("expirationYear", "card is expired")));
    }

    @Test
    void amexRequiresFourDigitCvv() {
        assertThat(CardValidator.validate(AMEX, "1234", 1, 2030, "A B", NOW).brand()).isEqualTo(CardBrand.AMEX);
        assertThatThrownBy(() -> CardValidator.validate(AMEX, "123", 1, 2030, "A B", NOW))
                .isInstanceOf(InvalidCardException.class);
        assertThatThrownBy(() -> CardValidator.validate(VISA, "1234", 1, 2030, "A B", NOW))
                .isInstanceOf(InvalidCardException.class);
    }

    @Test
    void collectsAllErrorsAtOnce() {
        assertThatThrownBy(() -> CardValidator.validate(VISA_BAD_LUHN, "12a", 13, 2030, " ", NOW))
                .isInstanceOf(InvalidCardException.class)
                .satisfies(e -> {
                    InvalidCardException ex = (InvalidCardException) e;
                    assertThat(ex.code()).isEqualTo("INVALID_CARD");
                    assertThat(ex.fieldErrors()).extracting(FieldError::field)
                            .containsExactly("cardNumber", "cvv", "expirationMonth", "holderName");
                });
    }

    @Test
    void rejectsUnsupportedBrandWrongLengthAndNonDigits() {
        assertThatThrownBy(() -> CardValidator.validate("9999999999999995", "123", 1, 2030, "A", NOW))
                .satisfies(e -> assertThat(((InvalidCardException) e).fieldErrors())
                        .contains(new FieldError("cardNumber", "card brand not supported")));
        assertThatThrownBy(() -> CardValidator.validate("41111111111111", "123", 1, 2030, "A", NOW))
                .satisfies(e -> assertThat(((InvalidCardException) e).fieldErrors())
                        .contains(new FieldError("cardNumber", "invalid length for VISA")));
        assertThatThrownBy(() -> CardValidator.validate("abc", null, 1, 2030, null, NOW))
                .satisfies(e -> assertThat(((InvalidCardException) e).fieldErrors()).extracting(FieldError::field)
                        .containsExactly("cardNumber", "cvv", "holderName"));
    }

    @Test
    void cardDataToStringIsMasked() {
        String text = new CardData(VISA, 12, 2029, "Andrés Neri").toString();
        assertThat(text).doesNotContain(VISA).doesNotContain("Andrés").doesNotContain("2029").contains("1111");
        assertThat(new CardData(null, 1, 2030, "x").toString()).contains("****");
    }
}
