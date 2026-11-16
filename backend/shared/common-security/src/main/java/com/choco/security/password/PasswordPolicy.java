package com.choco.security.password;

import com.choco.core.error.ValidationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Minimum acceptable password strength. Enforced server-side on registration and reset, because a
 * client-side check is a convenience for the user and not a control.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 128;

    /** Very common passwords are rejected outright; a full breach corpus belongs in a data store. */
    private static final Set<String> BLOCKLIST = Set.of(
            "password", "password1", "password123", "1234567890", "qwertyuiop",
            "letmein123", "welcome123", "admin12345", "iloveyou123", "chocolate");

    private PasswordPolicy() {
    }

    public static void validate(String rawPassword) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (rawPassword == null || rawPassword.isBlank()) {
            throw new ValidationException("Password is required", Map.of("password", "must not be empty"));
        }
        if (rawPassword.length() < MIN_LENGTH) {
            errors.put("password", "must be at least " + MIN_LENGTH + " characters");
        }
        if (rawPassword.length() > MAX_LENGTH) {
            errors.put("password", "must not exceed " + MAX_LENGTH + " characters");
        }
        if (!rawPassword.chars().anyMatch(Character::isLetter)) {
            errors.put("password", "must contain at least one letter");
        }
        if (!rawPassword.chars().anyMatch(Character::isDigit)) {
            errors.put("password", "must contain at least one digit");
        }
        if (BLOCKLIST.contains(rawPassword.toLowerCase(java.util.Locale.ROOT))) {
            errors.put("password", "is too common; choose a different password");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Password does not meet the security policy", errors);
        }
    }
}
