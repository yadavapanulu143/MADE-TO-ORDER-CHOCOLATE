package com.choco.identity.domain.customer;

import com.choco.core.domain.EmailAddress;
import com.choco.core.domain.PhoneNumber;
import java.util.UUID;

/**
 * A customer's contact identity.
 *
 * <p>Email and mobile are value objects that normalise and validate on construction, so an invalid
 * or duplicate-causing form cannot be stored. Password material is deliberately absent: the hash
 * lives in a separate entity so it can never be serialised into a customer DTO by accident.
 */
public record ContactDetails(
        EmailAddress email,
        PhoneNumber mobile,
        String fullName) {

    private static final int MIN_NAME_LENGTH = 2;
    private static final int MAX_NAME_LENGTH = 120;

    public ContactDetails {
        if (email == null) {
            throw new IllegalArgumentException("Email address is required");
        }
        if (mobile == null) {
            throw new IllegalArgumentException("Mobile number is required");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }
        String trimmed = fullName.trim().replaceAll("\\s+", " ");
        if (trimmed.length() < MIN_NAME_LENGTH || trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Full name must be between " + MIN_NAME_LENGTH + " and " + MAX_NAME_LENGTH + " characters");
        }
        fullName = trimmed;
    }

    public static ContactDetails of(String fullName, String email, String mobile) {
        return new ContactDetails(EmailAddress.of(email), PhoneNumber.of(mobile), fullName);
    }

    /** Masked form for logs and admin lists, e.g. {@code a***e@example.com}. */
    public String maskedEmail() {
        String value = email.value();
        int at = value.indexOf('@');
        if (at <= 1) {
            return "***" + value.substring(at);
        }
        return value.charAt(0) + "***" + value.charAt(at - 1) + value.substring(at);
    }

    public String maskedMobile() {
        String value = mobile.value();
        return "******" + value.substring(value.length() - 4);
    }
}
