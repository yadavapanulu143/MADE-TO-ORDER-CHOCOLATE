package com.choco.core.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Email address normalised to lower case so that {@code A@x.com} and {@code a@x.com} cannot
 * register as two separate accounts.
 */
public record EmailAddress(String value) {

    private static final Pattern FORMAT =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int MAX_LENGTH = 254;

    public EmailAddress {
        if (value == null) {
            throw new IllegalArgumentException("Email address is required");
        }
        String normalised = value.trim().toLowerCase(Locale.ROOT);
        if (normalised.length() > MAX_LENGTH || !FORMAT.matcher(normalised).matches()) {
            throw new IllegalArgumentException("Invalid email address");
        }
        value = normalised;
    }

    public static EmailAddress of(String value) {
        return new EmailAddress(value);
    }

    /**
     * Parses without throwing.
     *
     * <p>Used where a caller supplies either an email or a mobile number and the input simply has to
     * be classified: a malformed value must not raise here, because the caller's job is to decide
     * which lookup to attempt, and the eventual "not found" answer covers both cases.
     */
    public static java.util.Optional<EmailAddress> tryParse(String value) {
        try {
            return java.util.Optional.of(new EmailAddress(value));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
