package com.farmatodo.challenge.customer.domain;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.farmatodo.challenge.shared.error.DomainException.FieldError;
import com.farmatodo.challenge.shared.error.Exceptions.InvalidInputException;

/**
 * Canonical forms used for storage and uniqueness:
 * <ul>
 *   <li>email: trimmed and lower-cased ({@code Andres@Mail.com} == {@code andres@mail.com})</li>
 *   <li>phone: E.164 ({@code +58 412-123.45.67} → {@code +584121234567})</li>
 * </ul>
 */
public final class ContactNormalizer {

    /** E.164: '+', country code not starting with 0, 8 to 15 digits in total. */
    private static final Pattern E164 = Pattern.compile("\\+[1-9]\\d{7,14}");

    private ContactNormalizer() {
    }

    public static String email(String raw) {
        return raw.strip().toLowerCase(Locale.ROOT);
    }

    public static String phone(String raw) {
        String compact = raw.replaceAll("[\\s().-]", "");
        if (!E164.matcher(compact).matches()) {
            throw new InvalidInputException("VALIDATION_ERROR", "Invalid phone number",
                    List.of(new FieldError("phone", "must be in international format, e.g. +584121234567")));
        }
        return compact;
    }
}
