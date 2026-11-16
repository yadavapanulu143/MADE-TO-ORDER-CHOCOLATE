package com.choco.identity.adapter.out.security;

import com.choco.identity.application.port.OpaqueTokenHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * SHA-256 hashing for opaque, high-entropy tokens.
 *
 * <p>SHA-256 is the right tool here, not Argon2. A reset token is 256 bits of random data, so there
 * is no dictionary or guessing attack for a slow hash to blunt; the only requirement is that a
 * database dump does not yield usable tokens, which a plain digest satisfies. Using the password
 * hasher instead would also be functionally broken, because its salt makes each digest unique and
 * the token could never be found by lookup again.
 */
@Component
class Sha256OpaqueTokenHasher implements OpaqueTokenHasher {

    @Override
    public String hash(String tokenValue) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(tokenValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }
}
