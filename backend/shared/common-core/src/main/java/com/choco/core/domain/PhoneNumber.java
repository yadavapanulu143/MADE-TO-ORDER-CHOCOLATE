package com.choco.core.domain;

import java.util.regex.Pattern;

/**
 * Indian mobile number normalised to the 10-digit national form. The country code is not stored
 * because the business delivers only within India.
 */
public record PhoneNumber(String value) {

    private static final Pattern FORMAT = Pattern.compile("[6-9][0-9]{9}");
    private static final Pattern COUNTRY_PREFIX = Pattern.compile("^(\\+91|91|0)");

    public PhoneNumber {
        if (value == null) {
            throw new IllegalArgumentException("Mobile number is required");
        }
        String raw = value.trim().replaceAll("[\\s-]", "");
        // A national number is exactly 10 digits, so a prefix is only ever present on a longer input.
        // Stripping unconditionally would corrupt any valid number that begins with 91: the prefix
        // pattern matches the literal "91", so 9123456780 would lose its first two digits and be
        // rejected as too short. Gating on length leaves 10-digit inputs untouched.
        String digits = raw.length() > 10 ? COUNTRY_PREFIX.matcher(raw).replaceFirst("") : raw;
        if (!FORMAT.matcher(digits).matches()) {
            throw new IllegalArgumentException("Invalid Indian mobile number");
        }
        value = digits;
    }

    public static PhoneNumber of(String value) {
        return new PhoneNumber(value);
    }

    /** Parses without throwing; see {@link EmailAddress#tryParse(String)} for the rationale. */
    public static java.util.Optional<PhoneNumber> tryParse(String value) {
        try {
            return java.util.Optional.of(new PhoneNumber(value));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }

    public String withCountryCode() {
        return "+91" + value;
    }

    @Override
    public String toString() {
        return value;
    }
}
