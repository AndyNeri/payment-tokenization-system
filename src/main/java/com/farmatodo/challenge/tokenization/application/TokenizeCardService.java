package com.farmatodo.challenge.tokenization.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;

import com.farmatodo.challenge.customer.CustomerDirectory;
import com.farmatodo.challenge.shared.error.Exceptions.NotFoundException;
import com.farmatodo.challenge.shared.tracing.TransactionContext;
import com.farmatodo.challenge.tokenization.CardTokenized;
import com.farmatodo.challenge.tokenization.TokenizationRejected;
import com.farmatodo.challenge.tokenization.domain.Card;
import com.farmatodo.challenge.tokenization.domain.CardValidator;
import com.farmatodo.challenge.tokenization.domain.CardValidator.ValidatedCard;
import com.farmatodo.challenge.tokenization.domain.EncryptedCard;
import com.farmatodo.challenge.tokenization.domain.Ports.CardEncryptor;
import com.farmatodo.challenge.tokenization.domain.Ports.CardVault;
import com.farmatodo.challenge.tokenization.domain.Ports.TokenGenerator;
import com.farmatodo.challenge.tokenization.domain.RejectionPolicy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tokenization flow (see docs/sequences.md §1):
 * validate → customer exists → rejection policy → generate token → encrypt (AAD = token) → store → event.
 * <p>
 * Order matters: invalid data is a 400 regardless of the dice roll, and the random rejection only applies to
 * requests that would otherwise succeed.
 */
@Service
class TokenizeCardService implements TokenizeCardUseCase {

    private static final Logger log = LoggerFactory.getLogger(TokenizeCardService.class);
    static final String RANDOM_REJECTION = "RANDOM_REJECTION";

    private final CardVault vault;
    private final CardEncryptor encryptor;
    private final TokenGenerator tokenGenerator;
    private final RejectionPolicy rejectionPolicy;
    private final CustomerDirectory customers;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    TokenizeCardService(CardVault vault, CardEncryptor encryptor, TokenGenerator tokenGenerator,
            RejectionPolicy rejectionPolicy, CustomerDirectory customers, ApplicationEventPublisher events,
            Clock clock) {
        this.vault = vault;
        this.encryptor = encryptor;
        this.tokenGenerator = tokenGenerator;
        this.rejectionPolicy = rejectionPolicy;
        this.customers = customers;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TokenizationResult tokenize(TokenizeCardCommand command) {
        ValidatedCard validated = CardValidator.validate(command.cardNumber(), command.cvv(),
                command.expirationMonth(), command.expirationYear(), command.holderName(), YearMonth.now(clock));
        // From here on the CVV is no longer referenced: it is discarded with the command.

        if (!customers.exists(command.customerId())) {
            throw new NotFoundException("CUSTOMER_NOT_FOUND", "Customer " + command.customerId() + " does not exist");
        }

        Instant now = clock.instant();
        if (rejectionPolicy.shouldReject()) {
            log.info("Tokenization rejected by policy (p={}) for customer {} card {}",
                    rejectionPolicy.rejectionProbability(), command.customerId(), validated.last4());
            events.publishEvent(new TokenizationRejected(command.customerId(), validated.brand().name(),
                    validated.last4(), RANDOM_REJECTION, TransactionContext.currentOrNew(), now));
            return new TokenizationResult.Rejected(RANDOM_REJECTION);
        }

        String token = tokenGenerator.generate();
        EncryptedCard encrypted = encryptor.encrypt(validated.data(), token.getBytes(StandardCharsets.UTF_8));
        Card saved = vault.save(new Card(null, token, command.customerId(), validated.brand(), validated.last4(),
                encrypted, now));

        log.info("Card tokenized: customer {} brand {} last4 {}", saved.customerId(), saved.brand(), saved.last4());
        events.publishEvent(new CardTokenized(saved.id(), saved.customerId(), saved.brand().name(), saved.last4(),
                TransactionContext.currentOrNew(), now));
        return new TokenizationResult.Tokenized(saved);
    }
}
