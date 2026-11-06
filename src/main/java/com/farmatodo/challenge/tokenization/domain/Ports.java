package com.farmatodo.challenge.tokenization.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound ports of the tokenization slice. The domain/application depend on these interfaces;
 * infrastructure adapters implement them (JPA, AES-GCM, SecureRandom...).
 */
public final class Ports {

    private Ports() {
    }

    /** Persistence of tokenized cards (the "vault"). */
    public interface CardVault {
        Card save(Card card);

        Optional<Card> findByToken(String token);

        List<Card> findByCustomer(UUID customerId);
    }

    /** Authenticated encryption of card data. {@code associatedData} binds the ciphertext to its record. */
    public interface CardEncryptor {
        EncryptedCard encrypt(CardData data, byte[] associatedData);

        CardData decrypt(EncryptedCard encrypted, byte[] associatedData);
    }

    /** Generates opaque, unguessable tokens unrelated to the PAN. */
    public interface TokenGenerator {
        String generate();
    }
}
