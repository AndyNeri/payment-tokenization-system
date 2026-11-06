package com.farmatodo.challenge.tokenization.domain;

/** Source of randomness for business simulations. Replaced by a fixed value in tests. */
@FunctionalInterface
public interface RandomProvider {

    /** @return a value in [0, 1) */
    double nextDouble();
}
