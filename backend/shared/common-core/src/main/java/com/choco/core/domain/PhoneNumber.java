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
        String digits = COUNTRY_PREFIX.matcher(value.trim().replaceAll("[\\s-]", "")).replaceFirst("");
        if (!FORMAT.matcher(digits).matches()) {
            throw new IllegalArgumentException("Invalid Indian mobile number");
        }
        value = digits;
    }

    public static PhoneNumber of(String value) {
        return new PhoneNumber(value);
    }

    public String withCountryCode() {
        return "+91" + value;
    }

    @Override
    public String toString() {
        return value;
    }
}
