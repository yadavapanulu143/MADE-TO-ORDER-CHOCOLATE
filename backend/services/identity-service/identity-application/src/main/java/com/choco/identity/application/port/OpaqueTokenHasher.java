package com.choco.identity.application.port;

/**
 * Hashes opaque, high-entropy tokens that are looked up by their hash.
 *
 * <p>This is deliberately not {@code PasswordHasher}. A password hash is salted so that two equal
 * passwords produce different digests, which is exactly what makes it useless for lookup: storing a
 * salted digest and later searching for it can never match, because the second call uses a fresh
 * salt. A reset token is not a password — it is 256 bits of random data with no dictionary to attack
 * — so a fast, deterministic digest is both safe and required for the token to be found at all.
 *
 * <p>As with any token hash, the digest is compared in constant time.
 */
public interface OpaqueTokenHasher {

    /** Returns a deterministic, non-reversible digest of {@code tokenValue}. */
    String hash(String tokenValue);
}
