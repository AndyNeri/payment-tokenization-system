package com.farmatodo.challenge.shared.error;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.farmatodo.challenge.shared.tracing.TransactionContext;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Builds RFC 9457 ProblemDetail bodies with this API's extensions:
 * {@code code} (stable, machine-readable), {@code transactionId} and optional {@code errors[]}.
 */
public final class ProblemDetails {

    public static final String TYPE_BASE = "https://api.payment-tokenization.dev/errors/";
    public static final String CODE = "code";
    public static final String TRANSACTION_ID = "transactionId";
    public static final String ERRORS = "errors";

    private ProblemDetails() {
    }

    public static ProblemDetail of(HttpStatusCode status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return enrich(problem, code);
    }

    /** Adds {@code type}, {@code code} and {@code transactionId} to a ProblemDetail (keeps an existing code). */
    public static ProblemDetail enrich(ProblemDetail problem, String code) {
        Map<String, Object> properties = problem.getProperties();
        String effectiveCode = properties != null && properties.get(CODE) instanceof String existing ? existing : code;
        problem.setType(URI.create(TYPE_BASE + effectiveCode.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setProperty(CODE, effectiveCode);
        TransactionContext.current().ifPresent(id -> problem.setProperty(TRANSACTION_ID, id.toString()));
        return problem;
    }

    public static ProblemDetail withErrors(ProblemDetail problem, List<DomainException.FieldError> errors) {
        if (!errors.isEmpty()) {
            problem.setProperty(ERRORS, errors);
        }
        return problem;
    }

    /** Default code derived from the HTTP status, e.g. 405 → METHOD_NOT_ALLOWED. */
    public static String defaultCode(HttpStatusCode status) {
        return status instanceof org.springframework.http.HttpStatus known ? known.name() : "HTTP_" + status.value();
    }
}
