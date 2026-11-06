package com.farmatodo.challenge.tokenization.infrastructure.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.farmatodo.challenge.tokenization.domain.Card;
import com.farmatodo.challenge.tokenization.domain.EncryptedCard;
import com.farmatodo.challenge.tokenization.domain.Ports.CardVault;

import org.springframework.stereotype.Component;

/** Outbound adapter: implements the domain's {@link CardVault} port with Spring Data JPA. */
@Component
class JpaCardVault implements CardVault {

    private final CardJpaRepository repository;

    JpaCardVault(CardJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Card save(Card card) {
        EncryptedCard encrypted = card.encrypted();
        CardJpaEntity entity = new CardJpaEntity(card.token(), card.customerId(), encrypted.ciphertext(),
                encrypted.iv(), encrypted.keyId(), card.last4(), card.brand(), card.createdAt());
        return toDomain(repository.save(entity));
    }

    @Override
    public Optional<Card> findByToken(String token) {
        return repository.findByToken(token).map(JpaCardVault::toDomain);
    }

    @Override
    public List<Card> findByCustomer(UUID customerId) {
        return repository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(JpaCardVault::toDomain)
                .toList();
    }

    private static Card toDomain(CardJpaEntity e) {
        return new Card(e.getId(), e.getToken(), e.getCustomerId(), e.getBrand(), e.getLast4(),
                new EncryptedCard(e.getEncryptedPayload(), e.getIv(), e.getKeyId()), e.getCreatedAt());
    }

}
