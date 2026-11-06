package com.farmatodo.challenge.shared.error;

import java.util.List;

/**
 * Generic concrete exceptions. Slices may throw these directly with their own code
 * (e.g. {@code new NotFoundException("CUSTOMER_NOT_FOUND", "...")}) or extend them for more expressive names.
 */
public final class Exceptions {

    private Exceptions() {
    }

    public static class InvalidInputException extends DomainException {
        public InvalidInputException(String code, String message) {
            super(ErrorType.INVALID_INPUT, code, message);
        }

        public InvalidInputException(String code, String message, List<FieldError> fieldErrors) {
            super(ErrorType.INVALID_INPUT, code, message, fieldErrors);
        }
    }

    public static class UnauthorizedException extends DomainException {
        public UnauthorizedException(String code, String message) {
            super(ErrorType.UNAUTHORIZED, code, message);
        }
    }

    public static class NotFoundException extends DomainException {
        public NotFoundException(String code, String message) {
            super(ErrorType.NOT_FOUND, code, message);
        }
    }

    public static class ConflictException extends DomainException {
        public ConflictException(String code, String message) {
            super(ErrorType.CONFLICT, code, message);
        }
    }

    public static class BusinessRuleException extends DomainException {
        public BusinessRuleException(String code, String message) {
            super(ErrorType.BUSINESS_RULE, code, message);
        }
    }
}
