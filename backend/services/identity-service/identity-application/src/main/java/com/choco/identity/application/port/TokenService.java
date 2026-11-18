package com.choco.identity.application.port;

import com.choco.security.principal.AuthenticatedUser;
import java.time.Duration;
import java.time.Instant;

/**
 * Outbound port for issuing and validating tokens, so the application layer does not depend on JWT
 * specifics.
 */
public interface TokenService {

    /**
     * Issues an access token bound to a session.
     *
     * <p>The {@code sessionId} becomes the token's {@code jti} claim. Binding the token to the session
     * that produced it is what makes logout mean something: revoking the session revokes every access
     * token minted alongside it, so a logged-out client cannot keep using a token that is still
     * cryptographically valid.
     */
    IssuedToken issueAccessToken(AuthenticatedUser user, String sessionId, Instant now);

    /** Generates an opaque refresh token. Only its hash is persisted by the caller. */
    String generateRefreshTokenValue();

    String hashRefreshToken(String tokenValue);

    /**
     * The access-token lifetime.
     *
     * <p>Exposed so a caller revoking a session can retain the revocation record for exactly as long as
     * any access token of that session could still be presented — no shorter (which would let a token
     * outlive its revocation) and no longer (which would waste store).
     */
    Duration accessTokenTtl();
}
