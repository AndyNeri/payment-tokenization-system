package com.farmatodo.challenge.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.farmatodo.challenge.shared.error.DomainException.ErrorType;
import com.farmatodo.challenge.shared.error.DomainException.FieldError;
import com.farmatodo.challenge.shared.error.Exceptions.BusinessRuleException;
import com.farmatodo.challenge.shared.error.Exceptions.ConflictException;
import com.farmatodo.challenge.shared.error.Exceptions.InvalidInputException;
import com.farmatodo.challenge.shared.error.Exceptions.NotFoundException;
import com.farmatodo.challenge.shared.error.Exceptions.UnauthorizedException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class GlobalExceptionHandlerTest {

    final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @ParameterizedTest
    @EnumSource(ErrorType.class)
    void everyErrorTypeMapsToAnHttpStatus(ErrorType type) {
        assertThat(GlobalExceptionHandler.statusOf(type)).isNotNull();
    }

    @Test
    void domainExceptionsBecomeProblemDetailsWithTheirCode() {
        assertProblem(handler.handleDomain(new InvalidInputException("INVALID_CARD", "bad card")), 400, "INVALID_CARD");
        assertProblem(handler.handleDomain(new UnauthorizedException("INVALID_CREDENTIALS", "nope")), 401, "INVALID_CREDENTIALS");
        assertProblem(handler.handleDomain(new NotFoundException("CARD_NOT_FOUND", "missing")), 404, "CARD_NOT_FOUND");
        assertProblem(handler.handleDomain(new ConflictException("EMAIL_ALREADY_EXISTS", "dup")), 409, "EMAIL_ALREADY_EXISTS");
        assertProblem(handler.handleDomain(new BusinessRuleException("TOKENIZATION_REJECTED", "rejected")), 422, "TOKENIZATION_REJECTED");
    }

    @Test
    void includesFieldErrorsAndRfc9457Type() {
        var ex = new InvalidInputException("INVALID_CARD", "bad card",
                List.of(new FieldError("cardNumber", "fails Luhn check")));
        ProblemDetail problem = handler.handleDomain(ex);

        assertThat(problem.getType().toString()).isEqualTo(ProblemDetails.TYPE_BASE + "invalid-card");
        assertThat(problem.getDetail()).isEqualTo("bad card");
        assertThat(problem.getProperties()).containsEntry(ProblemDetails.ERRORS, ex.fieldErrors());
    }

    @Test
    void infrastructureConflictsBecome409() {
        assertProblem(handler.handleOptimisticLock(new OptimisticLockingFailureException("v")), 409, "CONCURRENT_MODIFICATION");
        assertProblem(handler.handleDataIntegrity(new DataIntegrityViolationException("dup")), 409, "DATA_INTEGRITY_VIOLATION");
    }

    @Test
    void unexpectedErrorsDoNotLeakInternals() {
        ProblemDetail problem = handler.handleUnexpected(new IllegalStateException("SELECT * FROM secret_table failed"));
        assertProblem(problem, 500, "INTERNAL_ERROR");
        assertThat(problem.getDetail()).doesNotContain("secret_table");
    }

    @Test
    void enrichKeepsAnExistingCode() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setProperty(ProblemDetails.CODE, "SPECIFIC");
        ProblemDetails.enrich(problem, "GENERIC");
        assertThat(problem.getProperties()).containsEntry(ProblemDetails.CODE, "SPECIFIC");
        assertThat(ProblemDetails.defaultCode(HttpStatus.METHOD_NOT_ALLOWED)).isEqualTo("METHOD_NOT_ALLOWED");
    }

    private static void assertProblem(ProblemDetail problem, int status, String code) {
        assertThat(problem.getStatus()).isEqualTo(status);
        assertThat(problem.getProperties()).containsEntry(ProblemDetails.CODE, code);
    }
}
