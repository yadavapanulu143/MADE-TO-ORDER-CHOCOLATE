package com.choco.identity.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage for administrator refresh tokens.
 *
 * <p>Kept separate from {@link RefreshTokenRepository} so a customer session can never be looked up
 * as an administrator session. The two tables have the same shape but different owners, and sharing
 * one repository would make "revoke all sessions for this id" ambiguous about which kind of account
 * the id refers to.
 */
public interface AdminRefreshTokenRepository {

    void store(UUID id, UUID adminId, String tokenHash, Instant expiresAt, Instant issuedAt);

    Optional<StoredAdminRefreshToken> findByHash(String tokenHash);

    void revoke(UUID id, Instant revokedAt);

    /** Revokes every active token for an administrator, used on password change. */
    int revokeAllForAdmin(UUID adminId, Instant revokedAt);

    record StoredAdminRefreshToken(UUID id, UUID adminId, String tokenHash, Instant expiresAt,
                                   Instant issuedAt, Instant revokedAt) {

        public boolean isActive(Instant now) {
            return revokedAt == null && now.isBefore(expiresAt);
        }
    }
}
