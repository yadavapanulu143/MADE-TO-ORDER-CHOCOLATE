package com.choco.security.password;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;

/**
 * Argon2id password hashing, the primary algorithm for this platform.
 *
 * <p>Argon2id is memory-hard: it forces an attacker to spend memory as well as CPU per guess, which
 * makes GPU and ASIC cracking far more expensive than against a purely CPU-bound function. The id
 * variant mixes the data-independent first pass with data-dependent later passes, which resists
 * both side-channel and time-memory trade-off attacks.
 *
 * <p>Parameters are OWASP-recommended for the id variant: 19 MiB of memory, 2 iterations, 1 degree
 * of parallelism. Peak memory is therefore about 19 MiB per concurrent hash, which is the figure to
 * size the container heap against.
 */
public class Argon2PasswordHasher implements PasswordHasher {

    static final int SALT_LENGTH = 16;
    static final int HASH_LENGTH = 32;
    static final int PARALLELISM = 1;
    static final int MEMORY_KIB = 19 * 1024;
    static final int ITERATIONS = 2;
    static final int CURRENT_VERSION = Argon2Factory.Argon2Types.ARGON2id.ordinal();

    private final Argon2 argon2;

    public Argon2PasswordHasher() {
        this(Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id, SALT_LENGTH, HASH_LENGTH));
    }

    Argon2PasswordHasher(Argon2 argon2) {
        this.argon2 = argon2;
    }

    @Override
    public String hash(String rawPassword) {
        char[] chars = rawPassword.toCharArray();
        try {
            return argon2.hash(ITERATIONS, MEMORY_KIB, PARALLELISM, chars);
        } finally {
            // Wipe the transient copy so the plaintext is not left on the heap longer than needed.
            java.util.Arrays.fill(chars, '\0');
        }
    }

    @Override
    public boolean matches(String rawPassword, String encodedHash) {
        if (rawPassword == null || encodedHash == null || encodedHash.isBlank()) {
            return false;
        }
        char[] chars = rawPassword.toCharArray();
        try {
            return argon2.verify(encodedHash, chars);
        } finally {
            java.util.Arrays.fill(chars, '\0');
        }
    }

    @Override
    public boolean needsRehash(String encodedHash) {
        if (encodedHash == null || encodedHash.isBlank()) {
            return true;
        }
        return argon2.needsRehash(encodedHash, ITERATIONS, MEMORY_KIB, PARALLELISM);
    }
}
