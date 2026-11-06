package com.farmatodo.challenge.tokenization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RejectionPolicyTest {

    @ParameterizedTest(name = "p={0}, r={1} -> reject={2}")
    @CsvSource({
            "0.0, 0.0,    false",   // p = 0 never rejects, even with the smallest r
            "1.0, 0.9999, true",    // p = 1 always rejects
            "0.2, 0.1999, true",    // r < p -> reject
            "0.2, 0.2,    false",   // boundary: r == p is accepted
            "0.2, 0.7,    false"
    })
    void rejectsWhenRandomIsBelowProbability(double p, double r, boolean expected) {
        assertThat(new RejectionPolicy(p, () -> r).shouldReject()).isEqualTo(expected);
    }

    @Test
    void observedRateMatchesConfiguredProbability() {
        SplittableRandom seeded = new SplittableRandom(42);
        RejectionPolicy policy = new RejectionPolicy(0.3, seeded::nextDouble);
        int rejected = 0;
        int runs = 100_000;
        for (int i = 0; i < runs; i++) {
            if (policy.shouldReject()) {
                rejected++;
            }
        }
        assertThat(rejected / (double) runs).isBetween(0.29, 0.31);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 1.01})
    void refusesProbabilityOutsideZeroOne(double p) {
        assertThatThrownBy(() -> new RejectionPolicy(p, () -> 0.5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exposesConfiguredProbability() {
        assertThat(new RejectionPolicy(0.25, () -> 0.5).rejectionProbability()).isEqualTo(0.25);
    }
}
