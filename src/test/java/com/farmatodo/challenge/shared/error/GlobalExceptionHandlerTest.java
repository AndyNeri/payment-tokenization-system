package com.farmatodo.challenge.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.farmatodo.challenge.StandaloneMvc;
import com.farmatodo.challenge.shared.error.Exceptions.BusinessRuleException;
import com.farmatodo.challenge.shared.error.Exceptions.ConflictException;
import com.farmatodo.challenge.shared.error.Exceptions.InvalidInputException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Every failure becomes an RFC 9457 ProblemDetail with a stable code and the transactionId; never a stacktrace. */
class GlobalExceptionHandlerTest {

    @RestController
    static class Failing {
        @GetMapping("/invalid")
        void invalid() {
            throw new InvalidInputException("INVALID_CARD", "bad card",
                    List.of(new DomainException.FieldError("cardNumber", "fails Luhn check")));
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException("EMAIL_ALREADY_EXISTS", "duplicate");
        }

        @GetMapping("/rule")
        void rule() {
            throw new BusinessRuleException("TOKENIZATION_REJECTED", "rejected");
        }

        @GetMapping("/lock")
        void lock() {
            throw new OptimisticLockingFailureException("stale");
        }

        @GetMapping("/integrity")
        void integrity() {
            throw new DataIntegrityViolationException("uq_x");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("secret internal detail");
        }

        @PostMapping("/body")
        void body(@RequestBody java.util.Map<String, Object> body) {
        }
    }

    MockMvcTester mvc = StandaloneMvc.of(new Failing());

    @Test
    void domainExceptionsMapToTheirStatusWithFieldErrors() {
        assertThat(mvc.get().uri("/invalid")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[0].field").isEqualTo("cardNumber");
        assertThat(mvc.get().uri("/conflict")).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("EMAIL_ALREADY_EXISTS");
        org.slf4j.MDC.put("transactionId", "5f0c0000-0000-4000-8000-000000000001");   // as TransactionIdFilter does
        try {
            assertThat(mvc.get().uri("/rule")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                    .bodyJson().extractingPath("$.transactionId").isEqualTo("5f0c0000-0000-4000-8000-000000000001");
        } finally {
            org.slf4j.MDC.clear();
        }
    }

    @Test
    void databaseConflictsAre409() {
        assertThat(mvc.get().uri("/lock")).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(mvc.get().uri("/integrity")).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("DATA_INTEGRITY_VIOLATION");
    }

    @Test
    void unexpectedErrorsHideTheirDetails() throws Exception {
        var result = mvc.get().uri("/boom").exchange();
        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson().extractingPath("$.code").isEqualTo("INTERNAL_ERROR");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("secret internal detail");
    }

    @Test
    void malformedJsonAndUnknownRoutes() {
        assertThat(mvc.post().uri("/body").contentType(MediaType.APPLICATION_JSON).content("{malo"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
        assertThat(mvc.post().uri("/invalid")).hasStatus(HttpStatus.METHOD_NOT_ALLOWED)
                .bodyJson().extractingPath("$.code").isNotNull();
    }

    @Test
    void everyErrorTypeHasAStatus() {
        for (DomainException.ErrorType type : DomainException.ErrorType.values()) {
            assertThat(GlobalExceptionHandler.statusOf(type)).isNotNull();
        }
    }
}
