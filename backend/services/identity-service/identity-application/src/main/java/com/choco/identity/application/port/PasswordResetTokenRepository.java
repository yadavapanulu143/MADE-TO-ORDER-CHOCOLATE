package com.choco.identity.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Storage for single-use password reset tokens, held as hashes. */
public interface PasswordResetTokenRepository {

    void store(UUID id, UUID customerId, String tokenHash, Instant expiresAt, Instant issuedAt);

    Optional<StoredResetToken> findByHash(String tokenHash);

    void consume(UUID id, Instant consumedAt);

    /** Invalidates every outstanding token for a customer, so only the newest link can be used. */
    int consumeAllForCustomer(UUID customerId, Instant consumedAt);

    record StoredResetToken(UUID id, UUID customerId, String tokenHash, Instant expiresAt,
                            Instant issuedAt, Instant consumedAt) {

        public boolean isActive(Instant now) {
            return consumedAt == null && now.isBefore(expiresAt);
        }
    }
}
