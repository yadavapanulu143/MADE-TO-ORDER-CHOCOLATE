package com.choco.core.domain;

import java.util.regex.Pattern;

/**
 * Indian postal code. Validated as six digits whose first digit is 1-9, per the India Post
 * numbering scheme; a leading zero is not a real delivery PIN.
 */
public record Pincode(String value) {

    private static final Pattern FORMAT = Pattern.compile("[1-9][0-9]{5}");

    public Pincode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid Indian PIN code: " + value);
        }
    }

    public static Pincode of(String value) {
        return new Pincode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
