package com.choco.security.password;

import java.util.Locale;

/**
 * Chooses the hashing algorithm from configuration.
 *
 * <p>Both algorithms are supported because existing hashes must keep verifying after a switch:
 * {@code DelegatingPasswordHasher} reads the algorithm from the stored hash prefix.
 */
public final class PasswordHasherFactory {

    public static final String ARGON2ID = "argon2id";
    public static final String BCRYPT = "bcrypt";

    private PasswordHasherFactory() {
    }

    public static PasswordHasher create(String algorithm) {
        if (algorithm == null) {
            return new Argon2PasswordHasher();
        }
        return switch (algorithm.toLowerCase(Locale.ROOT)) {
            case ARGON2ID -> new Argon2PasswordHasher();
            case BCRYPT -> new BCryptPasswordHasher();
            default -> throw new IllegalArgumentException(
                    "Unsupported password hashing algorithm: " + algorithm
                            + " (supported: argon2id, bcrypt)");
        };
    }
}
