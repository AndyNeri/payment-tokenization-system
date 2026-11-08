package com.farmatodo.challenge.tokenization.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.farmatodo.challenge.tokenization.domain.Card;
import com.farmatodo.challenge.tokenization.domain.CardBrand;
import com.farmatodo.challenge.tokenization.domain.EncryptedCard;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** The vault stores only ciphertext + IV + key id, last4 and brand; never the PAN or the CVV. */
class JpaCardVaultTest {

    CardJpaRepository springData = mock(CardJpaRepository.class);
    JpaCardVault vault = new JpaCardVault(springData);
    UUID customer = UUID.randomUUID();
    Instant now = Instant.parse("2026-11-08T10:00:00Z");
    Card card = new Card(null, "tok_abc", customer, CardBrand.VISA, "1111",
            new EncryptedCard(new byte[] {9, 8, 7}, new byte[12], "k1"), now);

    CardJpaEntity stored() {
        CardJpaEntity e = new CardJpaEntity("tok_abc", customer, new byte[] {9, 8, 7}, new byte[12], "k1", "1111",
                CardBrand.VISA, now);
        ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
        return e;
    }

    @Test
    void saveKeepsOnlyEncryptedDataAndMapsBack() {
        when(springData.save(any())).thenAnswer(inv -> {
            CardJpaEntity e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
            return e;
        });

        Card saved = vault.save(card);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.token()).isEqualTo("tok_abc");
        assertThat(saved.encrypted().ciphertext()).containsExactly(9, 8, 7);
        assertThat(saved.encrypted().keyId()).isEqualTo("k1");
        assertThat(saved.last4()).isEqualTo("1111");
    }

    @Test
    void lookups() {
        CardJpaEntity e = stored();
        when(springData.findByToken("tok_abc")).thenReturn(Optional.of(e));
        when(springData.findById(e.getId())).thenReturn(Optional.of(e));
        when(springData.findByCustomerIdOrderByCreatedAtDesc(customer)).thenReturn(List.of(e));

        assertThat(vault.findByToken("tok_abc")).get().extracting(Card::brand).isEqualTo(CardBrand.VISA);
        assertThat(vault.findById(e.getId())).get().extracting(Card::customerId).isEqualTo(customer);
        assertThat(vault.findByCustomer(customer)).hasSize(1);
        assertThat(vault.findByToken("tok_other")).isEmpty();
    }
}
