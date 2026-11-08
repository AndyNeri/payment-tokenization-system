package com.farmatodo.challenge.tokenization.infrastructure.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.farmatodo.challenge.StandaloneMvc;
import com.farmatodo.challenge.tokenization.application.CustomerCardsService;
import com.farmatodo.challenge.tokenization.application.TokenizeCardUseCase;
import com.farmatodo.challenge.tokenization.application.TokenizeCardUseCase.TokenizationResult;
import com.farmatodo.challenge.tokenization.domain.Card;
import com.farmatodo.challenge.tokenization.domain.CardBrand;
import com.farmatodo.challenge.tokenization.domain.EncryptedCard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

class TokenControllerTest {

    TokenizeCardUseCase useCase = mock(TokenizeCardUseCase.class);
    CustomerCardsService customerCards = mock(CustomerCardsService.class);
    MockMvcTester mvc = StandaloneMvc.of(new TokenController(useCase), new MyCardsController(customerCards));
    UUID customer = UUID.randomUUID();
    Card card = new Card(UUID.randomUUID(), "tok_abc", customer, CardBrand.VISA, "1111",
            new EncryptedCard(new byte[] {1}, new byte[12], "k1"), Instant.parse("2026-11-08T10:00:00Z"));

    String body(String number, String cvv) {
        return """
                {"customerId":"%s","cardNumber":"%s","cvv":"%s","expirationMonth":12,"expirationYear":2030,
                 "holderName":"Ana Pérez"}""".formatted(customer, number, cvv);
    }

    @AfterEach
    void tearDown() {
        StandaloneMvc.logout();
    }

    @Test
    void tokenizedCardIs201WithoutCardData() throws Exception {
        when(useCase.tokenize(any())).thenReturn(new TokenizationResult.Tokenized(card));

        var result = mvc.post().uri("/api/v1/tokens").contentType(MediaType.APPLICATION_JSON)
                .content(body("4111111111111111", "123")).exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.token").isEqualTo("tok_abc");
        assertThat(result).bodyJson().extractingPath("$.last4").isEqualTo("1111");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("4111111111111111").doesNotContain("cvv");
    }

    @Test
    void randomRejectionIs422() {
        when(useCase.tokenize(any())).thenReturn(new TokenizationResult.Rejected("RANDOM_REJECTION"));

        assertThat(mvc.post().uri("/api/v1/tokens").contentType(MediaType.APPLICATION_JSON)
                .content(body("4111111111111111", "123")))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.code").isEqualTo("TOKENIZATION_REJECTED");
    }

    @Test
    void malformedFieldsAre400AndNeverReachTheUseCase() {
        assertThat(mvc.post().uri("/api/v1/tokens").contentType(MediaType.APPLICATION_JSON)
                .content(body("4111-abc", "12")))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[*].field").asArray().contains("cardNumber", "cvv");
        verifyNoInteractions(useCase);
    }

    @Test
    void requestToStringMasksThePanAndCvv() {
        var request = new TokenizationDtos.TokenizeCardRequest(customer, "4111111111111111", "123", 12, 2030, "Ana");
        assertThat(request.toString()).doesNotContain("4111111111111111").doesNotContain("123").contains("****");
    }

    @Test
    void myCardsListsTheLoggedInCustomersCards() {
        StandaloneMvc.loggedInAs(customer);
        when(customerCards.cardsOf(customer)).thenReturn(List.of(card));

        assertThat(mvc.get().uri("/api/v1/me/cards")).hasStatusOk()
                .bodyJson().extractingPath("$[0].brand").isEqualTo("VISA");
    }
}
