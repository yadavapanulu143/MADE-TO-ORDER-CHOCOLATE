package com.choco.payment.domain.idempotency;

import java.time.Instant;
import java.util.UUID;

/**
 * Remembers the outcome of a request that must not happen twice.
 *
 * <p>Creating a provider order is the operation a client retries after a timeout, and it must not
 * produce two provider orders. Scoped by operation as well as key so the same key used elsewhere does
 * not collide, and the stored fingerprint lets a replay with a different body be refused rather than
 * answered with the first request's result.
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
