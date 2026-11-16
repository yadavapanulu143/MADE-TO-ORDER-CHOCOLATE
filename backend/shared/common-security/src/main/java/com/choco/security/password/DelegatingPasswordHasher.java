package com.choco.security.password;

import java.util.List;

/**
 * Verifies a stored hash with whichever algorithm produced it, and always hashes new passwords with
 * the preferred (first) hasher.
 *
 * <p>This is what makes a migration from BCrypt to Argon2id possible without forcing every user to
 * reset: old hashes keep working, and each user is transparently upgraded on their next successful
 * login because {@link #needsRehash} reports the old format.
 */
public class DelegatingPasswordHasher implements com.choco.core.port.PasswordHasher {

    private final com.choco.core.port.PasswordHasher preferred;
    private final List<com.choco.core.port.PasswordHasher> candidates;

    public DelegatingPasswordHasher(com.choco.core.port.PasswordHasher preferred, List<com.choco.core.port.PasswordHasher> candidates) {
        this.preferred = preferred;
        this.candidates = List.copyOf(candidates);
    }

    @Override
    public String hash(String rawPassword) {
        return preferred.hash(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedHash) {
        for (com.choco.core.port.PasswordHasher candidate : candidates) {
            if (candidate.matches(rawPassword, encodedHash)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean needsRehash(String encodedHash) {
        if (encodedHash == null || encodedHash.isBlank()) {
            return true;
        }
        return !isPreferredFormat(encodedHash) || preferred.needsRehash(encodedHash);
    }

    private boolean isPreferredFormat(String encodedHash) {
        return encodedHash.startsWith("$argon2id$") && preferred instanceof Argon2PasswordHasher;
    }
}
