package com.choco.security.password;

/**
 * Hashes and verifies passwords. The domain and use-case layers depend on this abstraction, so the
 * algorithm can be swapped or upgraded without touching business logic.
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String encodedHash);

    /** True when the stored hash was produced with weaker parameters and should be re-hashed. */
    boolean needsRehash(String encodedHash);
}
