package com.choco.identity.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage for refresh tokens. Tokens are stored as hashes so a database leak does not yield usable
 * credentials.
 */
public interface RefreshTokenRepository {

    void store(UUID id, UUID customerId, String tokenHash, Instant expiresAt, Instant issuedAt);

    Optional<StoredRefreshToken> findByHash(String tokenHash);

    /** Revokes a single token, used on rotation and on logout. */
    void revoke(UUID id, Instant revokedAt);

    /** Revokes every active token for a customer, used when a password changes. */
    int revokeAllForCustomer(UUID customerId, Instant revokedAt);

    record StoredRefreshToken(UUID id, UUID customerId, String tokenHash, Instant expiresAt,
                              Instant issuedAt, Instant revokedAt) {

        public boolean isActive(Instant now) {
            return revokedAt == null && now.isBefore(expiresAt);
        }
    }
}
