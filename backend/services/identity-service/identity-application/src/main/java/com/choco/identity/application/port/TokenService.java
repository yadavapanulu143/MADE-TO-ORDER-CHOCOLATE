package com.choco.identity.application.port;

import com.choco.security.principal.AuthenticatedUser;
import java.time.Instant;

/**
 * Outbound port for issuing and validating tokens, so the application layer does not depend on JWT
 * specifics.
 */
public interface TokenService {

    IssuedToken issueAccessToken(AuthenticatedUser user, Instant now);

    /** Generates an opaque refresh token. Only its hash is persisted by the caller. */
    String generateRefreshTokenValue();

    String hashRefreshToken(String tokenValue);
}
