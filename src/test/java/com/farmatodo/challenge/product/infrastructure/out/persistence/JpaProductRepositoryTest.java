package com.farmatodo.challenge.product.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JpaProductRepositoryTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ibuprofeno | %ibuprofeno%",
            "100%       | %100\\%%",
            "a_b        | %a\\_b%",
            "c:\\x      | %c:\\\\x%"
    })
    void escapesLikeWildcards(String query, String expected) {
        assertThat(JpaProductRepository.containsPattern(query)).isEqualTo(expected);
    }
}
