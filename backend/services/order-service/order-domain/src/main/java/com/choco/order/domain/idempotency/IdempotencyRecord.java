package com.choco.order.domain.idempotency;

import java.time.Instant;
import java.util.UUID;

/**
 * Remembers the outcome of a request that must not happen twice.
 *
 * <p>Scoped by operation as well as key, so the same key used for "place order" and "initiate payment"
 * does not collide. The stored request fingerprint lets a replay with a <em>different</em> body be
 * rejected rather than answered with the first request's result — silently returning the wrong result
 * for a genuinely different request would be worse than refusing it.
 */
public record IdempotencyRecord(
        UUID id,
        String scope,
        String key,
        String requestFingerprint,
        String resourceId,
        String responseBody,
        int responseStatus,
        Instant createdAt,
        Instant expiresAt) {

    public IdempotencyRecord {
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("An idempotency scope is required");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("An idempotency key is required");
        }
        if (createdAt == null || expiresAt == null) {
            throw new IllegalArgumentException("An idempotency record must be timestamped");
        }
    }

    public static IdempotencyRecord of(UUID id, String scope, String key, String fingerprint,
                                       String resourceId, String responseBody, int responseStatus,
                                       Instant createdAt, Instant expiresAt) {
        return new IdempotencyRecord(id, scope, key, fingerprint, resourceId, responseBody,
                responseStatus, createdAt, expiresAt);
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
