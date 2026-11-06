package com.farmatodo.challenge.tokenization.domain;

/**
 * Simulated rejection of tokenization requests with a configurable probability (challenge requirement).
 * <p>
 * Semantics (ADR-007): draw r in [0, 1) and reject when {@code r < p}. So p = 0 never rejects, p = 1 always
 * rejects, and p = 0.2 rejects ~20% of requests. The randomness comes through the {@link RandomProvider} port,
 * which makes the behaviour deterministic in tests.
 */
public final class RejectionPolicy {

    private final double rejectionProbability;
    private final RandomProvider random;

    public RejectionPolicy(double rejectionProbability, RandomProvider random) {
        if (rejectionProbability < 0.0 || rejectionProbability > 1.0) {
            throw new IllegalArgumentException("rejectionProbability must be within [0, 1]");
        }
        this.rejectionProbability = rejectionProbability;
        this.random = random;
    }

    public boolean shouldReject() {
        return random.nextDouble() < rejectionProbability;
    }

    public double rejectionProbability() {
        return rejectionProbability;
    }
}
