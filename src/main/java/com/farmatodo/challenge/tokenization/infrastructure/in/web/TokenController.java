package com.farmatodo.challenge.tokenization.infrastructure.in.web;

import com.farmatodo.challenge.shared.error.Exceptions.BusinessRuleException;
import com.farmatodo.challenge.tokenization.application.TokenizeCardUseCase;
import com.farmatodo.challenge.tokenization.application.TokenizeCardUseCase.TokenizeCardCommand;
import com.farmatodo.challenge.tokenization.application.TokenizeCardUseCase.TokenizationResult;
import com.farmatodo.challenge.tokenization.infrastructure.in.web.TokenizationDtos.CardResponse;
import com.farmatodo.challenge.tokenization.infrastructure.in.web.TokenizationDtos.TokenizeCardRequest;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Machine-to-machine tokenization endpoint, protected by X-API-Key (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/tokens")
class TokenController {

    private final TokenizeCardUseCase tokenizeCard;

    TokenController(TokenizeCardUseCase tokenizeCard) {
        this.tokenizeCard = tokenizeCard;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CardResponse tokenize(@Valid @RequestBody TokenizeCardRequest request) {
        var command = new TokenizeCardCommand(request.customerId(), request.cardNumber(), request.cvv(),
                request.expirationMonth(), request.expirationYear(), request.holderName());
        return switch (tokenizeCard.tokenize(command)) {
            case TokenizationResult.Tokenized tokenized -> CardResponse.from(tokenized.card());
            case TokenizationResult.Rejected rejected -> throw new BusinessRuleException("TOKENIZATION_REJECTED",
                    "The tokenization was rejected (" + rejected.reason() + "). Please retry.");
        };
    }
}
