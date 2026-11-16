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

    @Override
    public String toString() {
        return value;
    }
}
