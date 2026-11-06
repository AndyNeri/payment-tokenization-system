package com.farmatodo.challenge.tokenization.application;

import java.util.List;
import java.util.UUID;

import com.farmatodo.challenge.tokenization.domain.Card;
import com.farmatodo.challenge.tokenization.domain.Ports.CardVault;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lists a customer's tokenized cards (masked: brand + last 4 only). */
@Service
public class CustomerCardsService {

    private final CardVault vault;

    CustomerCardsService(CardVault vault) {
        this.vault = vault;
    }

    @Transactional(readOnly = true)
    public List<Card> cardsOf(UUID customerId) {
        return vault.findByCustomer(customerId);
    }
}
