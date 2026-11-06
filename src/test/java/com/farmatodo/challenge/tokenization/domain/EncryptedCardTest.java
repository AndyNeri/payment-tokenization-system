package com.farmatodo.challenge.tokenization.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class EncryptedCardTest {

    @Test
    void isImmutableAndComparesByContent() {
        byte[] ciphertext = {1, 2, 3};
        EncryptedCard card = new EncryptedCard(ciphertext, new byte[12], "k1");
        ciphertext[0] = 9;                    // mutating the input must not affect the record
        card.ciphertext()[1] = 9;             // nor mutating the returned copy

        assertThat(card.ciphertext()).containsExactly(1, 2, 3);
        assertThat(card).isEqualTo(new EncryptedCard(new byte[] {1, 2, 3}, new byte[12], "k1"));
        assertThat(card).hasSameHashCodeAs(new EncryptedCard(new byte[] {1, 2, 3}, new byte[12], "k1"));
        assertThat(card).isNotEqualTo(new EncryptedCard(new byte[] {1, 2, 3}, new byte[12], "k2"));
        assertThat(card.toString()).contains("k1").contains("3 bytes");
    }

    @Test
    void cardOwnershipAndId() {
        UUID owner = UUID.randomUUID();
        Card card = new Card(null, "tok_x", owner, CardBrand.VISA, "1111",
                new EncryptedCard(new byte[1], new byte[12], "k1"), Instant.EPOCH);
        UUID id = UUID.randomUUID();

        assertThat(card.belongsTo(owner)).isTrue();
        assertThat(card.belongsTo(UUID.randomUUID())).isFalse();
        assertThat(card.withId(id).id()).isEqualTo(id);
    }
}
