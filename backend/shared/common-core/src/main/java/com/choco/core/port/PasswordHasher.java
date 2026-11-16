package com.choco.core.port;

/**
 * Outbound port for password hashing.
 *
 * <p>Lives here rather than in a security module because the domain must be able to say "hash this"
 * and "verify this" without depending on Spring Security or on a specific algorithm. A domain module
 * that imported a framework hashing class would fail the Clean Architecture build check, and rightly
 * so: the choice of Argon2id over BCrypt is a deployment decision, not a business rule.
 *
 * <p>A value returned by {@link #hash(String)} is opaque. Nothing outside the adapter may parse,
 * log or re-derive it.
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    /** Constant-time verification of a raw password against a stored hash. */
    boolean matches(String rawPassword, String storedHash);

    /** True when a stored hash was produced with an outdated algorithm or parameters. */
    boolean needsRehash(String storedHash);
}
