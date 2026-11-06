package com.farmatodo.challenge.tokenization.domain;

import java.util.Arrays;
import java.util.Objects;

/**
 * Output of the card encryptor.
 *
 * @param ciphertext encrypted {@link CardData} followed by the 128-bit GCM authentication tag
 * @param iv         12-byte initialisation vector, unique per encryption (not secret)
 * @param keyId      version of the key used, so keys can be rotated
 */
public record EncryptedCard(byte[] ciphertext, byte[] iv, String keyId) {

    public EncryptedCard {
        Objects.requireNonNull(ciphertext);
        Objects.requireNonNull(iv);
        Objects.requireNonNull(keyId);
        ciphertext = ciphertext.clone();
        iv = iv.clone();
    }

    @Override
    public byte[] ciphertext() {
        return ciphertext.clone();
    }

    @Override
    public byte[] iv() {
        return iv.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EncryptedCard other && Arrays.equals(ciphertext, other.ciphertext)
                && Arrays.equals(iv, other.iv) && keyId.equals(other.keyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(ciphertext), Arrays.hashCode(iv), keyId);
    }

    @Override
    public String toString() {
        return "EncryptedCard[keyId=" + keyId + ", " + ciphertext.length + " bytes]";
    }
}
