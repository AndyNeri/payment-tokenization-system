package com.farmatodo.challenge.tokenization.application;

import static com.farmatodo.challenge.tokenization.TestCards.VISA;
import static com.farmatodo.challenge.tokenization.TestCards.VISA_BAD_LUHN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.farmatodo.challenge.customer.CustomerDirectory;
import com.farmatodo.challenge.shared.error.Exceptions.NotFoundException;
import com.farmatodo.challenge.tokenization.CardTokenized;
import com.farmatodo.challenge.tokenization.TokenizationRejected;
import com.farmatodo.challenge.tokenization.application.TokenizeCardUseCase.TokenizeCardCommand;
import com.farmatodo.challenge.tokenization.application.TokenizeCardUseCase.TokenizationResult;
import com.farmatodo.challenge.tokenization.domain.Card;
import com.farmatodo.challenge.tokenization.domain.CardBrand;
import com.farmatodo.challenge.tokenization.domain.CardData;
import com.farmatodo.challenge.tokenization.domain.EncryptedCard;
import com.farmatodo.challenge.tokenization.domain.InvalidCardException;
import com.farmatodo.challenge.tokenization.domain.Ports.CardEncryptor;
import com.farmatodo.challenge.tokenization.domain.Ports.CardVault;
import com.farmatodo.challenge.tokenization.domain.RejectionPolicy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class TokenizeCardServiceTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T15:00:00Z"), ZoneOffset.UTC);
    static final UUID CUSTOMER = UUID.randomUUID();
    static final String TOKEN = "tok_fixed";

    CardVault vault = mock(CardVault.class);
    CardEncryptor encryptor = mock(CardEncryptor.class);
    CustomerDirectory customers = mock(CustomerDirectory.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    AtomicInteger randomCalls = new AtomicInteger();
    EncryptedCard encrypted = new EncryptedCard(new byte[] {1, 2, 3}, new byte[12], "k1");

    @BeforeEach
    void setUp() {
        when(customers.exists(CUSTOMER)).thenReturn(true);
        when(encryptor.encrypt(any(), any())).thenReturn(encrypted);
        when(vault.save(any())).thenAnswer(inv -> ((Card) inv.getArgument(0)).withId(UUID.randomUUID()));
    }

    TokenizeCardService service(double randomValue, double rejectionProbability) {
        RejectionPolicy policy = new RejectionPolicy(rejectionProbability, () -> {
            randomCalls.incrementAndGet();
            return randomValue;
        });
        return new TokenizeCardService(vault, encryptor, () -> TOKEN, policy, customers, events, CLOCK);
    }

    static TokenizeCardCommand command(String pan) {
        return new TokenizeCardCommand(CUSTOMER, pan, "123", 12, 2029, "Andres Neri");
    }

    @Test
    void tokenizesEncryptsWithTokenAsAadAndStoresOnlyMaskedData() {
        TokenizationResult result = service(0.9, 0.1).tokenize(command(VISA));

        Card card = ((TokenizationResult.Tokenized) result).card();
        assertThat(card.token()).isEqualTo(TOKEN);
        assertThat(card.brand()).isEqualTo(CardBrand.VISA);
        assertThat(card.last4()).isEqualTo("1111");
        assertThat(card.createdAt()).isEqualTo(CLOCK.instant());

        // What gets encrypted: PAN + expiration + holder. No CVV exists in CardData.
        ArgumentCaptor<CardData> data = ArgumentCaptor.forClass(CardData.class);
        ArgumentCaptor<byte[]> aad = ArgumentCaptor.forClass(byte[].class);
        verify(encryptor).encrypt(data.capture(), aad.capture());
        assertThat(data.getValue()).isEqualTo(new CardData(VISA, 12, 2029, "Andres Neri"));
        assertThat(new String(aad.getValue(), StandardCharsets.UTF_8)).isEqualTo(TOKEN);

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue()).isInstanceOfSatisfying(CardTokenized.class, e -> {
            assertThat(e.customerId()).isEqualTo(CUSTOMER);
            assertThat(e.last4()).isEqualTo("1111");
            assertThat(e.transactionId()).isNotNull();
        });
    }

    @Test
    void rejectionIsAResultNotAnExceptionAndIsAudited() {
        TokenizationResult result = service(0.05, 0.1).tokenize(command(VISA));

        assertThat(result).isEqualTo(new TokenizationResult.Rejected(TokenizeCardService.RANDOM_REJECTION));
        verify(vault, never()).save(any());
        verifyNoInteractions(encryptor);
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue()).isInstanceOf(TokenizationRejected.class);
    }

    @Test
    void invalidCardFailsBeforeRollingTheDice() {
        assertThatThrownBy(() -> service(0.0, 1.0).tokenize(command(VISA_BAD_LUHN)))
                .isInstanceOf(InvalidCardException.class);

        assertThat(randomCalls).hasValue(0);
        verifyNoInteractions(vault, encryptor, events);
    }

    @Test
    void unknownCustomerIs404AndDoesNotRollTheDice() {
        when(customers.exists(CUSTOMER)).thenReturn(false);

        assertThatThrownBy(() -> service(0.0, 1.0).tokenize(command(VISA)))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "CUSTOMER_NOT_FOUND");
        assertThat(randomCalls).hasValue(0);
        verifyNoInteractions(vault, encryptor, events);
    }

    @Test
    void commandToStringHidesCardData() {
        assertThat(command(VISA).toString()).doesNotContain(VISA).doesNotContain("123");
    }
}
