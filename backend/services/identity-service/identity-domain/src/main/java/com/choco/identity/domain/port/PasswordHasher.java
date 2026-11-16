package com.choco.identity.domain.port;

/**
 * Outbound port for password hashing.
 *
 * <p>The domain decides <em>when</em> a password must be hashed; the algorithm lives in an adapter.
 * A hash returned here is opaque: nothing outside the adapter may interpret or re-derive it.
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    /** Constant-time verification of a raw password against a stored hash. */
    boolean matches(String rawPassword, String storedHash);

    /** True when a stored hash was produced with an outdated algorithm or parameters. */
    boolean needsRehash(String storedHash);
}
