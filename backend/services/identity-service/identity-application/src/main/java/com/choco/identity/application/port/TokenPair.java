package com.choco.identity.application.port;

import java.time.Instant;

/**
 * An issued access/refresh token pair.
 *
 * <p>The refresh token is returned once, at issue time, and only its hash is stored. The access token
 * is short-lived and carries no refresh capability.
 */
public record TokenPair(
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        String tokenType) {

    public static TokenPair bearer(String accessToken, String refreshToken,
                                   Instant accessTokenExpiresAt, Instant refreshTokenExpiresAt) {
        return new TokenPair(accessToken, refreshToken, accessTokenExpiresAt, refreshTokenExpiresAt, "Bearer");
    }
}
