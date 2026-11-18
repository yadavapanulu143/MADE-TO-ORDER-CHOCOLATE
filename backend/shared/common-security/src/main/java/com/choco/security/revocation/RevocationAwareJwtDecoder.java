package com.choco.security.revocation;

import com.choco.core.port.TokenRevocationRegistry;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Wraps a {@link JwtDecoder} so a revoked session's token is refused before the request proceeds.
 *
 * <p>A bearer token is valid until it expires; nothing in the token itself says whether the session
 * that produced it was logged out. Verifying the signature is necessary but not sufficient — it proves
 * the token is genuine, not that it is still wanted. This decoder adds the second question: has the
 * session this token names been revoked? If so the token is rejected as if it had expired.
 *
 * <p><b>The token is identified by its {@code jti} claim,</b> which the identity service sets to the
 * session id (the refresh-token row id). Keying on the session rather than on each individual access
 * token is deliberate: revoking the session invalidates every access token it ever issued, so a client
 * that refreshed in another tab cannot keep a copy of the previous access token alive.
 *
 * <p><b>Fail-closed.</b> If the revocation store cannot be consulted, the request is refused rather
 * than allowed. An unavailable store must not become an open door for every session that was ever
 * logged out; the safe answer to "is this revoked?" when the answer is unknown is "treat it as
 * revoked". A token with no {@code jti} is likewise refused: the identity service always sets one, so
 * its absence means the token did not come from the expected issuer path, and silently trusting it
 * would be the same hole by another route.
 */
public class RevocationAwareJwtDecoder implements JwtDecoder {

    private final JwtDecoder delegate;
    private final TokenRevocationRegistry revocationRegistry;

    public RevocationAwareJwtDecoder(JwtDecoder delegate, TokenRevocationRegistry revocationRegistry) {
        this.delegate = delegate;
        this.revocationRegistry = revocationRegistry;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        // Signature, issuer and expiry are checked first: a forged or expired token never reaches the
        // store, so an attacker cannot use junk tokens to hammer Redis.
        Jwt jwt = delegate.decode(token);

        String sessionId = jwt.getId();
        if (sessionId == null || sessionId.isBlank()) {
            throw new JwtException(
                    "The token carries no session identifier and cannot be checked against the revocation list");
        }
        if (jwt.getIssuedAt() == null || jwt.getSubject() == null) {
            // The subject is the account a subject-wide cutoff is keyed on, and the issue time decides
            // whether a token predates that cutoff. Without both, the check cannot be answered.
            throw new JwtException(
                    "The token carries no subject or issue time and cannot be checked against the revocation list");
        }

        boolean revoked;
        try {
            revoked = revocationRegistry.isRevoked(sessionId, UUID.fromString(jwt.getSubject()), jwt.getIssuedAt());
        } catch (RuntimeException storeFailure) {
            // Fail closed. The store is the authority on whether a session is live; if it cannot be
            // reached, the only safe reading is that the token cannot be trusted right now.
            throw new JwtException("The revocation list is unavailable; refusing the token", storeFailure);
        }

        if (revoked) {
            throw new JwtException("The session for this token has been revoked");
        }
        return jwt;
    }
}
