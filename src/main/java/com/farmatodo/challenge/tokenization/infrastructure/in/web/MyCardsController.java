package com.farmatodo.challenge.tokenization.infrastructure.in.web;

import java.util.List;
import java.util.UUID;

import com.farmatodo.challenge.tokenization.application.CustomerCardsService;
import com.farmatodo.challenge.tokenization.infrastructure.in.web.TokenizationDtos.CardResponse;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The authenticated customer's own cards. The customer id comes from the JWT, never from the URL (no IDOR). */
@RestController
class MyCardsController {

    private final CustomerCardsService cards;

    MyCardsController(CustomerCardsService cards) {
        this.cards = cards;
    }

    @GetMapping("/api/v1/me/cards")
    List<CardResponse> myCards(@AuthenticationPrincipal Jwt jwt) {
        return cards.cardsOf(UUID.fromString(jwt.getSubject())).stream()
                .map(CardResponse::from)
                .toList();
    }
}
