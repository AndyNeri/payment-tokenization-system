package com.farmatodo.challenge.tokenization.domain;

/**
 * The sensitive card payload that gets encrypted (AES-256-GCM) before storage.
 * <p>
 * The CVV is intentionally NOT part of it: it is validated and discarded, never stored (PCI-DSS 3.2, ADR-005).
 * {@link #toString()} is masked so the PAN cannot leak through logs or exception messages.
 */
public record CardData(String pan, int expirationMonth, int expirationYear, String holderName) {

    @Override
    public String toString() {
        String last4 = pan == null || pan.length() < 4 ? "****" : pan.substring(pan.length() - 4);
        return "CardData[pan=**** " + last4 + ", expiration=**/****, holderName=***]";
    }
}
