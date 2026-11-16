package com.choco.security.password;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt fallback with the mandated work factor of 15.
 *
 * <p>Cost 15 means 2^15 key-expansion rounds. On current server hardware that is roughly 0.5-1.5
 * seconds per verification, which is a deliberate trade-off: it is slow enough to make offline
 * brute force expensive, yet still acceptable for interactive login. Because a single login costs
 * about a second of CPU, this algorithm is a fallback, not the default.
 */
public class BCryptPasswordHasher implements com.choco.core.port.PasswordHasher {

    public static final int COST_FACTOR = 15;

    private final BCryptPasswordEncoder encoder;

    public BCryptPasswordHasher() {
        this(COST_FACTOR);
    }

    public BCryptPasswordHasher(int costFactor) {
        if (costFactor < 10 || costFactor > 17) {
            throw new IllegalArgumentException("BCrypt cost factor must be between 10 and 17");
        }
        this.encoder = new BCryptPasswordEncoder(costFactor);
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedHash) {
        if (rawPassword == null || encodedHash == null || encodedHash.isBlank()) {
            return false;
        }
        return encoder.matches(rawPassword, encodedHash);
    }

    @Override
    public boolean needsRehash(String encodedHash) {
        return encodedHash == null || encodedHash.isBlank();
    }
}
