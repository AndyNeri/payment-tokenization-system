package com.farmatodo.challenge.tokenization.application;

import java.util.Optional;
import java.util.UUID;

import com.farmatodo.challenge.tokenization.CardLookup;
import com.farmatodo.challenge.tokenization.domain.Card;
import com.farmatodo.challenge.tokenization.domain.Ports.CardVault;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementation of the tokenization module API used by the order module. */
@Service
class CardLookupService implements CardLookup {

    private final CardVault vault;

    CardLookupService(CardVault vault) {
        this.vault = vault;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CardSummary> findOwned(String token, UUID customerId) {
        return vault.findByToken(token).filter(card -> card.belongsTo(customerId)).map(CardLookupService::summary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CardSummary> findById(UUID cardId) {
        return vault.findById(cardId).map(CardLookupService::summary);
    }

    private static CardSummary summary(Card card) {
        return new CardSummary(card.id(), card.token(), card.brand().name(), card.last4());
    }
}
