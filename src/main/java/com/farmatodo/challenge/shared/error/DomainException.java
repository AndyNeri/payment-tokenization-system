package com.farmatodo.challenge.shared.error;

import java.util.List;
import java.util.Objects;

/**
 * Base class for business errors raised by the domain and application layers.
 * <p>
 * It is deliberately free of HTTP/Spring types (hexagonal rule): the domain says <em>what</em> went wrong
 * ({@link ErrorType} + a stable {@code code}); the web adapter ({@code GlobalExceptionHandler}) decides
 * <em>how</em> to present it (HTTP status, RFC 9457 ProblemDetail).
 */
public abstract class DomainException extends RuntimeException {

    private final ErrorType type;
    private final String code;
    private final List<FieldError> fieldErrors;

    protected DomainException(ErrorType type, String code, String message) {
        this(type, code, message, List.of());
    }

    protected DomainException(ErrorType type, String code, String message, List<FieldError> fieldErrors) {
        super(message);
        this.type = Objects.requireNonNull(type);
        this.code = Objects.requireNonNull(code);
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public ErrorType type() {
        return type;
    }

    public String code() {
        return code;
    }

    public List<FieldError> fieldErrors() {
        return fieldErrors;
    }

    /** Category of a business error; mapped to an HTTP status by the web layer. */
    public enum ErrorType {
        INVALID_INPUT,   // 400
        UNAUTHORIZED,    // 401
        NOT_FOUND,       // 404
        CONFLICT,        // 409
        BUSINESS_RULE    // 422
    }

    /** A validation problem on a specific field. */
    public record FieldError(String field, String message) {
    }
}
