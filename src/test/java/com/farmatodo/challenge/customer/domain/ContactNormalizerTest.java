package com.farmatodo.challenge.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.farmatodo.challenge.shared.error.Exceptions.InvalidInputException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ContactNormalizerTest {

    @ParameterizedTest
    @CsvSource({
            "'  Andres.Neri@Mail.COM ', andres.neri@mail.com",
            "andres@mail.com, andres@mail.com"
    })
    void emailIsTrimmedAndLowerCased(String raw, String expected) {
        assertThat(ContactNormalizer.email(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"+584121234567", "+58 412 123 45 67", "+58 (412) 123-45.67", " +58-412-1234567 "})
    void phoneFormatsCollapseToE164(String raw) {
        assertThat(ContactNormalizer.phone(raw)).isEqualTo("+584121234567");
    }

    @ParameterizedTest
    @ValueSource(strings = {"04121234567", "+0412123456", "+58412", "+58412123456789012", "+58abc1234567"})
    void rejectsNonInternationalOrInvalidPhones(String raw) {
        assertThatThrownBy(() -> ContactNormalizer.phone(raw))
                .isInstanceOf(InvalidInputException.class)
                .satisfies(e -> assertThat(((InvalidInputException) e).fieldErrors())
                        .extracting("field").containsExactly("phone"));
    }
}
