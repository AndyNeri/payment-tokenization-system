package com.farmatodo.challenge.shared.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Single place that turns exceptions into RFC 9457 ProblemDetail responses.
 * <ul>
 *   <li>Extends {@link ResponseEntityExceptionHandler}, so Spring MVC's own errors (404 route, 405, 415, malformed
 *       JSON, validation...) already come out as ProblemDetail; we only enrich them with {@code code} and
 *       {@code transactionId}.</li>
 *   <li>Maps {@link DomainException} categories to HTTP statuses (the domain never knows about HTTP).</li>
 *   <li>Never leaks stack traces or internal messages: unexpected errors become a generic 500 and are logged with
 *       the transaction id.</li>
 * </ul>
 * Authentication errors (401) happen in the security filter chain before reaching controllers; they are
 * rendered in the same format by {@code ProblemDetailSecurityHandler}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomain(DomainException ex) {
        HttpStatus status = statusOf(ex.type());
        log.info("Business error {} ({}): {}", ex.code(), status.value(), ex.getMessage());
        ProblemDetail problem = ProblemDetails.of(status, ex.code(), status.getReasonPhrase(), ex.getMessage());
        return ProblemDetails.withErrors(problem, ex.fieldErrors());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Concurrent modification detected: {}", ex.getMessage());
        return ProblemDetails.of(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "Conflict",
                "The resource was modified concurrently, please retry");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        // Last line of defence (e.g. a race on a UNIQUE constraint). Slices translate known constraints themselves.
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ProblemDetails.of(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION", "Conflict",
                "The request conflicts with the current state of the data");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ProblemDetails.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal Server Error",
                "An unexpected error occurred. Report the transactionId to support.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<DomainException.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new DomainException.FieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        ProblemDetail problem = ProblemDetails.of(status, "VALIDATION_ERROR", "Bad Request",
                "One or more fields are invalid");
        return ResponseEntity.status(status).body(ProblemDetails.withErrors(problem, errors));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<DomainException.FieldError> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new DomainException.FieldError(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        ProblemDetail problem = ProblemDetails.of(status, "VALIDATION_ERROR", "Bad Request",
                "One or more parameters are invalid");
        return ResponseEntity.status(status).body(ProblemDetails.withErrors(problem, errors));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetails.of(status, "MALFORMED_REQUEST", "Bad Request",
                "The request body is missing or malformed");
        return ResponseEntity.status(status).body(problem);
    }

    /** Enriches every ProblemDetail produced by the base class (404 route, 405, 415...) with code + transactionId. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode statusCode,
            WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            ProblemDetails.enrich(problem, ProblemDetails.defaultCode(statusCode));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    static HttpStatus statusOf(DomainException.ErrorType type) {
        return switch (type) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case BUSINESS_RULE -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
    }
}
